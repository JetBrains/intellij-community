/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import sun.awt.image.IntegerComponentRaster;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.lang.ref.WeakReference;

/**
 * This class provides gradient paint with dithering (run main() to see the difference)<p/>
 * Note, it doesn't support "cyclic" mode.
 * Disclaimer: Unfortunately it works much slower than standard GradientPaint
 */
public class JBGradientPaint extends GradientPaint {
  public JBGradientPaint(float x1, float y1, Color color1, float x2, float y2, Color color2) {
    super(x1, y1, color1, x2, y2, color2);
  }

  public JBGradientPaint(Point2D pt1, Color color1, Point2D pt2, Color color2) {
    super(pt1, color1, pt2, color2);
  }

  @Override
  public PaintContext createContext(ColorModel cm,
                                    Rectangle deviceBounds,
                                    Rectangle2D userBounds,
                                    AffineTransform xform,
                                    RenderingHints hints) {
    return new JBGradientPaintContext(cm, getPoint1(), getPoint2(), xform, getColor1(), getColor2());
  }

  public static void main(String[] args) {
    final Color c1 = new Color(155, 155, 155);
    final Color c2 = new Color(50, 50, 50);
    final int size = 500;
    JFrame f = new JFrame("JBGradientPaint");
    JPanel contentPane = new JPanel(new GridLayout(1, 2, 1, 1));
    f.setContentPane(contentPane);
    JPanel leftPanel = new JPanel(new BorderLayout()){
      @Override
      public void paintComponent(Graphics g) {
        ((Graphics2D)g).setPaint(new GradientPaint(0, 0, c1, size/2, size, c2));
        g.fillRect(0, 0, size, size);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(size, size);
      }
    };
    JPanel rightPanel = new JPanel(new BorderLayout()){
      @Override
      public void paintComponent(Graphics g) {
        ((Graphics2D)g).setPaint(new JBGradientPaint(0, 0, c1, size/2, size, c2));
        g.fillRect(0, 0, size, size);
      }

      @Override
      public Dimension getPreferredSize() {
        return new Dimension(size, size);
      }
    };
    leftPanel.add(new JLabel("Standard gradient"), BorderLayout.NORTH);
    rightPanel.add(new JLabel("Dithered gradient"), BorderLayout.NORTH);
    leftPanel.setOpaque(true);
    rightPanel.setOpaque(true);
    contentPane.add(leftPanel);
    contentPane.add(rightPanel);
    f.pack();
    f.setLocationRelativeTo(null);
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    f.setVisible(true);
  }

  /**
   * Most of the code copied from java.awt.GradientPaintContext
   */
  private static class JBGradientPaintContext implements PaintContext {
    private static final ColorModel RGBMODEL = new DirectColorModel(24, 0x00ff0000, 0x0000ff00, 0x000000ff);
    private static final ColorModel BGRMODEL = new DirectColorModel(24, 0x000000ff, 0x0000ff00, 0x00ff0000);
    private static final ThreadLocal<int[][]> RGB_ARRAYS = new ThreadLocal<int[][]>() {
      @Override
      protected int[][] initialValue() {
        return new int[3][256];
      }
    };
    private static final ThreadLocal<int[][][][]> DITHER_ARRAYS = new ThreadLocal<int[][][][]>() {
      @Override
      protected int[][][][] initialValue() {
        return new int[3][256][][];
      }
    };
    //Dithering
    private static final double GR = (Math.sqrt(5) - 1) / 2;
    private static final int[] IDENTITY_FUNC = new int[256];
    private static final int[] SHIFT_FUNC = new int[256];
    private static final int[][][] DITHER_MATRIX = new int[256][256][];
    static {
      for (int i = 0; i < 256; i++) {
        IDENTITY_FUNC[i] = i;
        SHIFT_FUNC[i] = i + 1;
      }
      SHIFT_FUNC[255] = 255;
      int iter = 0;
      for (int i = 0; i < DITHER_MATRIX.length; i++) {
        int[][] row = DITHER_MATRIX[i];
        for (int j = 0; j < row.length; j++) {
          row[j] = IDENTITY_FUNC;
        }
        for (int j = 0; j < i; j++) {
          int pos = (int)(1604419 * GR * iter) % (256 - j);
          row[getIndex(row, pos)] = SHIFT_FUNC;
          iter++;
        }
      }
    }
    private static ColorModel cachedModel;
    private static WeakReference<Raster> cached;

    private final double myX1;
    private final double myY1;
    private final double myDx;
    private final double myDy;
    private final int myRgb1;
    private final int myRgb2;
    private Raster saved;
    private final ColorModel model;

    JBGradientPaintContext(ColorModel cm,
                           Point2D p1, Point2D p2, AffineTransform xform,
                           Color c1, Color c2) {
      Point2D xvec = new Point2D.Double(1, 0);
      Point2D yvec = new Point2D.Double(0, 1);
      try {
        AffineTransform inverse = xform.createInverse();
        inverse.deltaTransform(xvec, xvec);
        inverse.deltaTransform(yvec, yvec);
      }
      catch (NoninvertibleTransformException e) {
        xvec.setLocation(0, 0);
        yvec.setLocation(0, 0);
      }

      double udx = p2.getX() - p1.getX();
      double udy = p2.getY() - p1.getY();
      double ulenSq = udx * udx + udy * udy;

      if (ulenSq <= Double.MIN_VALUE) {
        myDx = 0;
        myDy = 0;
      }
      else {
        double dxx = (xvec.getX() * udx + xvec.getY() * udy) / ulenSq;
        double dyy = (yvec.getX() * udx + yvec.getY() * udy) / ulenSq;

        if (dxx < 0) {
          p1 = p2;
          Color c = c1;
          c1 = c2;
          c2 = c;
          myDx = -dxx;
          myDy = -dyy;
        }
        else {
          myDx = dxx;
          myDy = dyy;
        }
      }

      Point2D dp1 = xform.transform(p1, null);
      this.myX1 = dp1.getX();
      this.myY1 = dp1.getY();

      myRgb1 = c1.getRGB();
      myRgb2 = c2.getRGB();
      int a1 = (myRgb1 >> 24) & 0xff;
      int r1 = (myRgb1 >> 16) & 0xff;
      int g1 = (myRgb1 >> 8) & 0xff;
      int b1 = (myRgb1) & 0xff;
      int da = ((myRgb2 >> 24) & 0xff) - a1;
      int dr = ((myRgb2 >> 16) & 0xff) - r1;
      int dg = ((myRgb2 >> 8) & 0xff) - g1;
      int db = ((myRgb2) & 0xff) - b1;
      ColorModel m;
      if (a1 == 0xff && da == 0) {
        m = RGBMODEL;
        if (cm instanceof DirectColorModel) {
          DirectColorModel dcm = (DirectColorModel)cm;
          int tmp = dcm.getAlphaMask();
          if ((tmp == 0 || tmp == 0xff) &&
              dcm.getRedMask() == 0xff &&
              dcm.getGreenMask() == 0xff00 &&
              dcm.getBlueMask() == 0xff0000) {
            m = BGRMODEL;
            tmp = r1;
            r1 = b1;
            b1 = tmp;
            tmp = dr;
            dr = db;
            db = tmp;
          }
        }
      }
      else {
        m = ColorModel.getRGBdefault();
      }
      model = m;

      for (int i = 0; i < 256; i++) {
        double rel = i / 256.0f;
        double rValue = r1 + dr * rel;
        double gValue = g1 + dg * rel;
        double bValue = b1 + db * rel;

        DITHER_ARRAYS.get()[0][i] = DITHER_MATRIX[(int)(rValue * 256) % 256];
        DITHER_ARRAYS.get()[1][i] = DITHER_MATRIX[(int)(gValue * 256) % 256];
        DITHER_ARRAYS.get()[2][i] = DITHER_MATRIX[(int)(bValue * 256) % 256];

        RGB_ARRAYS.get()[0][i] = (int)rValue;
        RGB_ARRAYS.get()[1][i] = (int)gValue;
        RGB_ARRAYS.get()[2][i] = (int)bValue;
      }
    }

    static synchronized Raster getCachedRaster(ColorModel cm, int w, int h) {
      if (cm == cachedModel) {
        if (cached != null) {
          Raster ras = cached.get();
          if (ras != null &&
              ras.getWidth() >= w &&
              ras.getHeight() >= h) {
            cached = null;
            return ras;
          }
        }
      }
      return cm.createCompatibleWritableRaster(w, h);
    }

    static synchronized void putCachedRaster(ColorModel cm, Raster ras) {
      if (cached != null) {
        Raster raster = cached.get();
        if (raster != null) {
          int cw = raster.getWidth();
          int ch = raster.getHeight();
          int iw = ras.getWidth();
          int ih = ras.getHeight();
          if (cw >= iw && ch >= ih) {
            return;
          }
          if (cw * ch >= iw * ih) {
            return;
          }
        }
      }
      cachedModel = cm;
      cached = new WeakReference<Raster>(ras);
    }

    private static int getIndex(int[][] arr, int pos) {
      for (int i = 0; i < arr.length; i++) {
        int[] f = arr[i];
        if (f == IDENTITY_FUNC) {
          pos--;
        }
        if (pos < 0) {
          return i;
        }
      }
      throw new IllegalArgumentException();
    }

    public void dispose() {
      if (saved != null) {
        putCachedRaster(model, saved);
        saved = null;
      }
    }

    public ColorModel getColorModel() {
      return model;
    }

    public Raster getRaster(int x, int y, int w, int h) {
      double rowrel = (x - myX1) * myDx + (y - myY1) * myDy;

      Raster rast = saved;
      if (rast == null || rast.getWidth() < w || rast.getHeight() < h) {
        rast = getCachedRaster(model, w, h);
        saved = rast;
      }
      IntegerComponentRaster irast = (IntegerComponentRaster)rast;
      int off = irast.getDataOffset(0);
      int adjust = irast.getScanlineStride() - w;
      int[] pixels = irast.getDataStorage();

      clipFillRaster(pixels, off, adjust, w, h, rowrel, myDx, myDy);

      return rast;
    }

    void clipFillRaster(int[] pixels, int off, int adjust, int w, int h, double rowrel, double dx, double dy) {
      while (--h >= 0) {
        double colrel = rowrel;
        int j = w;
        if (colrel <= 0.0) {
          int rgb = myRgb1;
          do {
            pixels[off++] = rgb;
            colrel += dx;
          }
          while (--j > 0 && colrel <= 0.0);
        }
        while (colrel < 1.0 && --j >= 0) {
          int offrel = off & 0xFF;
          int idx = (int)(colrel * 256);

          int rresult = DITHER_ARRAYS.get()[0][idx][offrel][RGB_ARRAYS.get()[0][idx]];
          int gresult = DITHER_ARRAYS.get()[1][idx][offrel][RGB_ARRAYS.get()[1][idx]];
          int bresult = DITHER_ARRAYS.get()[2][idx][offrel][RGB_ARRAYS.get()[2][idx]];

          pixels[off++] = (rresult << 16) | (gresult << 8) | bresult;
          colrel += dx;
        }
        if (j > 0) {
          int rgb = myRgb2;
          do {
            pixels[off++] = rgb;
          }
          while (--j > 0);
        }

        off += adjust;
        rowrel += dy;
      }
    }
  }
}
