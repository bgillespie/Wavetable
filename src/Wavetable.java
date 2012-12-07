import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 
 * TODO base render, drawpoint on -1.0 to +1.0 range
 */
public class Wavetable extends JFrame {
    
    private static final long serialVersionUID = 1L;
    
    private static final String PALETTE_PATH = "/usr/share/gimp/2.0/palettes/Royal.gpl";
    public static final String FRAME_PATH =
            "/home/bjg/framestore";
            // null;
    public static final int WIDTH       = 512;
    public static final int HEIGHT      = 512;
    public static final int NUM_THREADS = 4;
    public static final int NUM_BOBS    = 7;
    public static final int NUM_FRAMES  = 128;
    
    private final Bob[] bobs = new Bob[NUM_BOBS];
    
    private final int width, height;
    private final BufferedImage buffer;
    private final Graphics2D gfxBuffer;
    private final ConcurrentLinkedQueue<DrawPoint> renderQueue
        = new ConcurrentLinkedQueue<DrawPoint>();
    
    Palette p;
    
    
    
    /**
     * Create a new visible wavetable.
     * 
     * @param width
     * @param height
     */
    public Wavetable(int width, int height) {
        
        this.setTitle("Wavetable" );
        
        this.width  = width;
        this.height = height;
        buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        gfxBuffer = buffer.createGraphics();
        
        @SuppressWarnings("serial")
        JPanel renderPanel = new JPanel() {
            @Override
            public void paint(Graphics g) {
                g.drawImage(buffer, 0, 0, null);
            }
        };
        renderPanel.setSize(width, height);
        renderPanel.setMinimumSize(new java.awt.Dimension(width, height));
        this.getContentPane().add(renderPanel);
        
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.setMinimumSize(new java.awt.Dimension(width, height));
        this.pack();
        this.setVisible(true);
    }
    
    
    
    /**
     * Represents a floating vibrating bob in the water table.
     * 
     * @author bjg
     */
    class Bob {
        
        public static final double TWOPI = 2.0d * Math.PI;
        
        final double x, y, wavelength, phase, speed;
        
        /**
         * 
         * @param x
         * @param y
         * @param wavelength
         * @param phase phase offset of wave
         * @param speed speed of wave movement over time.
         */
        Bob(double x, double y, double wavelength, double phase, double speed) {
            this.x = x;
            this.y = y;
            this.wavelength = wavelength;
            this.phase = phase;
            this.speed = speed;
        }
        
        public double heightAt(double x, double y, double time) {
            return Math.cos(
                TWOPI * (
                    ( time * speed ) +
                    ( Math.sqrt(
                            Math.pow(this.x - x, 2) +
                            Math.pow(this.y - y, 2)
                            ) / wavelength
                    )
                ) );
        }
    }
    
    
    
    /**
     * A point to be drawn, for queueing.
     */
    class DrawPoint {
        final double x, y, height;
        public DrawPoint(double x, double y, double height) {
            this.x = x;
            this.y = y;
            this.height = height;
        }
    }
    
    
    
    /**
     * Renders all or part of a display at a given timestamp.
     */
    class Renderer implements Runnable {
        
        boolean finished = false;
        final int left, top, width, height;
        final double time;
        
        Renderer(int left, int top, int width, int height, double time) {
            this.left   = left;
            this.top    = top;
            this.width  = width;
            this.height = height;
            this.time   = time;
        }
        
        public void run() {
            final int right  = left + width - 1;
            final int bottom = top + height - 1;
            double avg;
            int x, y, c;
            double bsx, bsy;
            double bsw_calc = 1.0d / Wavetable.this.width;
            double bsh_calc = 1.0d / Wavetable.this.height;
            for (x = left; x <= right; x++) {
                for (y = top; y <= bottom; y++) {
                    avg = 0.0d;
                    bsx = x * bsw_calc;
                    bsy = y * bsh_calc;
                    
                    // find sum of waves at this point
                    for (c = 0; c < NUM_BOBS; c++) {
                        avg += bobs[c].heightAt(bsx, bsy, time);
                    }
                    
                    // find positive value of average of waves
                    avg = ((avg / NUM_BOBS) + 1.0d) / 2.0d;
                    renderQueue.add(new DrawPoint(bsx, bsy, avg));
                }
            }
            finished = true;
        }
        
        boolean isFinished() {
            return finished;
        }
    }
    
    
    
    static class Palette {
        Color[] palette = new Color[256];
        int len;
        
        private Palette(Color[] palette) {
            this.palette = palette;
            len = palette.length;
        }
        
        public static Palette fromFile(String filename) throws IOException {
            Color[] swatches = new Color[256];
            Pattern mach = Pattern.compile("^\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)");
            LineIterator it = FileUtils.lineIterator(new File(filename), "UTF-8");
            int i = 0;
            try {
              while (it.hasNext()) {
                Matcher m = mach.matcher(it.nextLine());
                if (!m.find()) continue;
                int r = Integer.parseInt(m.group(1));
                int g = Integer.parseInt(m.group(2));
                int b = Integer.parseInt(m.group(3));
                swatches[i++] = new Color(r, g, b);
              }
            } finally {
              it.close();
            }
            return new Palette(swatches);
        }
        
        public static Palette fromWaves(
                double redRepeat  , double redPhase,
                double greenRepeat, double greenPhase,
                double blueRepeat , double bluePhase) {
            Color[] palette = new Color[256];
            
            double angleInc = 2.0d * Math.PI;
            
            // *phase will be used as the angle for the colour component
            redPhase   *= angleInc;
            greenPhase *= angleInc;
            bluePhase  *= angleInc;
            
            angleInc /= 256.0d;
            
            // *repeat will be used as the increment for the colour component
            redRepeat   *= angleInc;
            greenRepeat *= angleInc;
            blueRepeat  *= angleInc;
            
            int r, g, b;
            
            for (int i = 0; i < 255; i++) {
                r = (int)((Math.sin(redPhase  ) + 1.0d) / 2.0d * 255.0d);
                g = (int)((Math.sin(greenPhase) + 1.0d) / 2.0d * 255.0d);
                b = (int)((Math.sin(bluePhase ) + 1.0d) / 2.0d * 255.0d);
                
                palette[i] = new Color(r, g, b);
                redPhase   += redRepeat  ;
                greenPhase += greenRepeat;
                bluePhase  += blueRepeat ;
            }
            
            return new Palette(palette);
        }
        
        public static Palette toBnW(Palette p) {
            int colourCount = p.palette.length;
            Color[] colours = new Color[colourCount];
            Palette q = new Palette(colours);
            
            Color pc;
            int comp;
            for (int i = 0; i < colourCount - 1; i++) {
                pc = p.palette[i];
                int brightness = ( ( pc.getGreen() * 4 ) + ( pc.getRed() * 2 ) + pc.getBlue() ) / 7;
                comp = (int)((sigmoid((brightness / 128.0d) - 1.0d)) * 255);
                q.palette[i] = new Color(comp, comp, comp);
            }
            return q;
        }
        
        public static double sigmoid(double t) {
            return 1.0d / ( 1.0d + Math.pow(Math.E, -(t * 6.0d)) );
        }
    }
    
    
    
    /**
     * Render a frame.
     * @param time time from 0.0 to 1.0.
     */
    void render(double time) {
        gfxBuffer.setColor(Color.BLACK);
        gfxBuffer.drawRect(0, 0, width, height);
        
        Renderer[] renderers = new Renderer[NUM_THREADS];
        int left = 0, sliceW = width / NUM_THREADS;
        for (int i = 0; i < NUM_THREADS - 1; i++) {
            renderers[i] = new Renderer(left, 0, sliceW, height, time);
            new Thread(renderers[i]).start();
            left += sliceW;
        }
        renderers[NUM_THREADS - 1] = new Renderer(left, 0, width - left, height, time);
        new Thread(renderers[NUM_THREADS - 1]).start();
        
        DrawPoint d;
        int x, y;
        RENDER: while (true) {
            while (!renderQueue.isEmpty()) {
                d = renderQueue.poll();
                if ( d == null ) {
                    continue RENDER;
                }
                x = (int)(d.x * width);
                y = (int)(d.y * height);
                gfxBuffer.setColor(colourTrans(d.height));
                gfxBuffer.drawLine(x, y, x, y);
            }
            // try { Thread.sleep(1); } catch (InterruptedException e) {}
            for (int i = 0; i < NUM_THREADS; i++)
                if (! renderers[i].isFinished() ) continue RENDER;
            if ( ! renderQueue.isEmpty() ) continue;
            break;
        }
        
        this.repaint();
    }
    
    
    
    Color colourTrans(double height) {
        int greyPart = (int)(height * 255);
        return p.palette[greyPart % p.len];
        // return new Color(greyPart, greyPart, greyPart);
    }
    
    
    
    public static void main(String[] args) throws IOException {
        Wavetable w = new Wavetable(WIDTH, HEIGHT);
        for (int i = 0; i < Wavetable.NUM_BOBS; i++)
            w.bobs[i] = w.new Bob(
                    (Math.random() * 10.0d) - 5.0d, // x
                    (Math.random() * 10.0d) - 5.0d, // y
                    Math.pow( Math.random() + 0.01d, 2.0 ) + 0.01d, // wavelen
                    0, // phase
                    (int)(Math.random() * 4.0) - 2.0 // speed
                );
        
        // w.p = Palette.fromFile(Wavetable.PALETTE_PATH);
        w.p = 
                //Palette.toBnW(
                Palette.fromWaves(2.0d, 0.00d, 0.5d, 0.75d, 3.0d, 0.75d)
                //)
                ;
        
        long[] t = new long[NUM_FRAMES];
        // BufferedImage[] frames = new BufferedImage[NUM_FRAMES];
        double phrame;
        for (int i = 0; i < NUM_FRAMES - 1; i++) {
            t[i] = System.currentTimeMillis();
            phrame = i;
            phrame /= NUM_FRAMES;
            w.render(phrame);
            t[i] = System.currentTimeMillis() - t[i];
            if ( FRAME_PATH != null)
                ImageIO.write(w.buffer, "png", new File(String.format("%s/image-%04d.png", FRAME_PATH, i + 1)));
            // frames[i] = w.buffer.;
            w.repaint();
            // try { Thread.sleep(40); } catch (InterruptedException e) {}
        }
        
        long t_total = 0;
        for (int i = 1; i < NUM_FRAMES; i++)
            t_total += t[i];
        t_total /= NUM_FRAMES;
        System.out.println("Average millis per frame: " + t_total);
        
        // convert   -delay 1   -loop 0   image-*.png   animation.gif
    }
}
