// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.paint;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.paint.PaintUtil.RoundingMode;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.WavePainter2D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.awt.font.LineMetrics;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Sergey.Malenkov
 * @author tav
 */
public enum EffectPainter2D implements RegionPainter2D<Font> {
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#LINE_UNDERSCORE
   */
  LINE_UNDERSCORE {
    /**
     * Draws a horizontal line under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, Font font) {
      if (!Registry.is("ide.text.effect.new")) {
        LinePainter2D.paint(g, x, y + 1, x + width, y + 1);
      }
      else {
        paintUnderline(g, x, y, width, height, font, 1, this);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#BOLD_LINE_UNDERSCORE
   */
  BOLD_LINE_UNDERSCORE {
    /**
     * Draws a bold horizontal line under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, Font font) {
      if (!Registry.is("ide.text.effect.new")) {
        int h = JBUI.scale(Registry.intValue("editor.bold.underline.height", 2));
        RectanglePainter2D.FILL.paint(g, x, y, width, h);
      }
      else {
        paintUnderline(g, x, y, width, height, font, 2, this);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#BOLD_DOTTED_LINE
   */
  BOLD_DOTTED_UNDERSCORE {
    /**
     * Draws a bold horizontal line of dots under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, Font font) {
      paintUnderline(g, x, y, width, height, font, 2, this);
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#WAVE_UNDERSCORE
   */
  WAVE_UNDERSCORE {
    /**
     * Draws a horizontal wave under a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height available space under text
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, Font font) {
      if (!Registry.is("ide.text.effect.new")) {
        WavePainter2D.forColor(g.getColor()).paint(g, x, x + width, y + height);
      }
      else if (Registry.is("ide.text.effect.new.metrics")) {
        paintUnderline(g, x, y, width, height, font, 3, this);
      }
      else if (width > 0 && height > 0) {
        Cached.WAVE_UNDERSCORE.paint(g, x, y, width, height, null);
      }
    }
  },
  /**
   * @see com.intellij.openapi.editor.markup.EffectType#STRIKEOUT
   */
  STRIKE_THROUGH {
    /**
     * Draws a horizontal line through a text.
     *
     * @param g      the {@code Graphics2D} object to render to
     * @param x      text position
     * @param y      text baseline
     * @param width  text width
     * @param height text height
     * @param font   optional font to calculate line metrics
     */
    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, Font font) {
      if (width > 0 && height > 0) {
        if (!Registry.is("ide.text.effect.new.metrics")) {
          drawLineCentered(g, x, y - height, width, height, 1, this);
        }
        else {
          if (font == null) font = g.getFont();
          LineMetrics metrics = font.getLineMetrics("", g.getFontRenderContext());
          double offset = PaintUtil.alignToInt(-metrics.getStrikethroughOffset(), g, RoundingMode.FLOOR);
          @SuppressWarnings("TestOnlyProblems")
          double thickness = PaintUtil.alignToInt(maybeScaleFontMetricsThickness(metrics.getStrikethroughThickness(), font), g, RoundingMode.FLOOR);
          drawLine(g, x, y - offset, width, thickness, this);
        }
      }
    }
  };

  private static double getMaxHeight(double height) {
    return height > 7 && Registry.is("ide.text.effect.new.scale") ? height / 2 : 3;
  }

  @SuppressWarnings("TestOnlyProblems")
  private static void paintUnderline(Graphics2D g, double x, double y, double width, double height, Font font, double thickness, EffectPainter2D painter) {
    if (width > 0 && height > 0) {
      if (Registry.is("ide.text.effect.new.metrics")) {
        if (font == null) font = g.getFont();
        RoundingMode roundingMode = !UIUtil.isJreHiDPIEnabled() || painter != WAVE_UNDERSCORE || font.getSize2D() / UISettings.getDefFontSize() > 1 ?
                                    RoundingMode.FLOOR : RoundingMode.CEIL;
        LineMetrics metrics = font.getLineMetrics("", g.getFontRenderContext());
        double devPixel = PaintUtil.devPixel(g);
        double underlineThickness = maybeScaleFontMetricsThickness(metrics.getUnderlineThickness(), font);
        double underlineOffset = Math.max(devPixel, metrics.getUnderlineOffset());

        thickness = PaintUtil.alignToInt(thickness * underlineThickness, g, roundingMode);
        double offset = Math.min(height - thickness, underlineOffset);

        if (offset < devPixel) {
          offset = height > 3 * devPixel ? devPixel : 0;
          thickness = PaintUtil.alignToInt(Math.min(thickness, height - offset), g, roundingMode);
        }
        else {
          offset = PaintUtil.alignToInt(offset, g, roundingMode);
        }
        drawLine(g, x, y + offset, width, thickness, painter);
      }
      else {
        if (height > 3) {
          double max = getMaxHeight(height);
          y += height - max;
          height = max;
          if (thickness > 1 && height > 3) {
            thickness = JBUI.scale((float)thickness);
          }
        }
        drawLineCentered(g, x, y, width, height, thickness, painter);
      }
    }
  }

  @TestOnly
  public static double maybeScaleFontMetricsThickness_TestOnly(double fontMetricsThickness, @NotNull Font font) {
    return maybeScaleFontMetricsThickness(fontMetricsThickness, font);
  }

  private static double maybeScaleFontMetricsThickness(double fontMetricsThickness, @NotNull Font font) {
    float fontScale = JBUI.getFontScale(font.getSize2D());
    float normalizedFontScale = font.getSize2D() / UISettings.getDefFontSize();
    if (normalizedFontScale > 1) {
      // k==1.0 with normalizedFontScale==1.0, k->0.5 fast enough with normalizedFontScale increasing
      double k = 1 / (Math.pow(normalizedFontScale, 2) + 1) + 0.5;
      fontScale *= k;
    }
    if (!UIUtil.isJreHiDPIEnabled()) fontScale = (int)fontScale;
    return Math.max(fontMetricsThickness, fontScale);
  }

  private static void drawLineCentered(Graphics2D g, double x, double y, double width, double height, double thickness, EffectPainter2D painter) {
    double offset = height - thickness;
    if (offset > 0) {
      y += offset - (offset / 2);
      height = thickness;
    }
    drawLine(g, x, y, width, height, painter);
  }

  private static void drawLine(Graphics2D g, double x, double y, double width, double height, EffectPainter2D painter) {
    if (painter == BOLD_DOTTED_UNDERSCORE) {
      double dx = (x % height + height) % height;
      double w = width + dx;
      double dw = (w % height + height) % height;
      Cached.BOLD_DOTTED_UNDERSCORE.paint(g, x - dx, y, dw == 0 ? w : w - dw + height, height, null);
    }
    else if (painter == WAVE_UNDERSCORE) {
      Cached.WAVE_UNDERSCORE.paint(g, x, y, width, height, null);
    }
    else {
      RectanglePainter2D.FILL.paint(g, x, y, width, height);
    }
  }

  private enum Cached implements RegionPainter2D<Paint> {
    BOLD_DOTTED_UNDERSCORE {
      @Override
      double getPeriod(double height) {
        return height;
      }

      @Override
      void paintImage(Graphics2D g, double width, double height, double period) {
        Double round = period <= 2 && !UIUtil.isJreHiDPI(g) ? null : period;
        for (int dx = 0; dx < width; dx += period + period) {
          RectanglePainter2D.FILL.paint(g, dx, 0, period, period, round, LinePainter2D.StrokeType.INSIDE, 1, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
        }
      }
    },
    WAVE_UNDERSCORE {
      private final BasicStroke THIN_STROKE = new BasicStroke(.7f);

      @Override
      double getPeriod(double height) {
        return Math.max((Registry.is("ide.text.effect.new.metrics") ? height : getMaxHeight(height)) - 1, 1);
      }

      @Override
      void paintImage(Graphics2D g, double width, double height, double period) {
        double dx = 0;
        double lower = height - 1;
        double upper = lower - period;
        if (Registry.is("ide.text.effect.new.metrics")) {
          if (height > 3) {
            double fix = height / 3;
            g.setStroke(new BasicStroke((float)fix));
            if (fix > 1) {
              fix = (fix - 1) / 2;
              lower -= fix;
              upper += fix;
            }
          }
          height += 2;
          if (g.getClass().getName().equals("com.intellij.util.HiDPIScaledGraphics")) {
            lower += .5;
            upper += .5;
          }
        }
        Path2D path = new Path2D.Double();
        path.moveTo(dx, lower);
        if (height < 6) {
          g.setStroke(THIN_STROKE);
          while (dx < width) {
            path.lineTo(dx += period, upper);
            path.lineTo(dx += period, lower);
          }
        }
        else {
          double size = period / 2;
          double prev = dx - size / 2;
          double center = (upper + lower) / 2;
          while (dx < width) {
            path.quadTo(prev += size, lower, dx += size, center);
            path.quadTo(prev += size, upper, dx += size, upper);
            path.quadTo(prev += size, upper, dx += size, center);
            path.quadTo(prev += size, lower, dx += size, lower);
          }
        }
        path.lineTo(width, lower);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.draw(path);
      }
    };

    // we should not recalculate caches when IDEA is on Retina and non-Retina
    private final ConcurrentHashMap<Long, BufferedImage> myNormalCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BufferedImage> myHiDPICache = new ConcurrentHashMap<>();

    abstract double getPeriod(double height);

    abstract void paintImage(Graphics2D g, double width, double height, double period);

    void paintImage(Graphics2D g, Paint paint, double width, double height, double period) {
      try {
        g.setPaint(paint);
        paintImage(g, width, height, period);
      }
      finally {
        g.dispose();
      }
    }

    BufferedImage getImage(Graphics2D g, Color color, double height) {
      Long key = color.getRGB() ^ ((long)(JBUI.sysScale(g) * height) << 32);
      ConcurrentHashMap<Long, BufferedImage> cache = UIUtil.isJreHiDPI(g) ? myHiDPICache : myNormalCache;
      return cache.computeIfAbsent(key, k -> createImage(g, color, height));
    }

    BufferedImage createImage(Graphics2D g, Paint paint, double height) {
      double period = getPeriod(height);
      int width = (int)period << (paint instanceof Color ? 8 : 1);
      BufferedImage image;
      if (UIUtil.isJreHiDPI(g)) {
        image = new JBHiDPIScaledImage(g, width, height, BufferedImage.TYPE_INT_ARGB);
      } else {
        image = UIUtil.createImage(g, width, (int)height, BufferedImage.TYPE_INT_ARGB);
      }
      paintImage(image.createGraphics(), paint, width, height, period);
      return image;
    }

    @Override
    public void paint(Graphics2D g, double x, double y, double width, double height, Paint paint) {
      if (paint == null) paint = g.getPaint();
      g = (Graphics2D)g.create();
      g.translate(x, y);
      g.clip(new Rectangle2D.Double(0, 0, width, height));

      g.setComposite(AlphaComposite.SrcOver);
      BufferedImage image = paint instanceof Color ? getImage(g, (Color)paint, height) : createImage(g, paint, height);
      int period = image.getWidth(null);
      if (image instanceof JBHiDPIScaledImage) period /= 2;
      double offset = (x % period + period) % period; // normalize
      g.translate(-offset, 0);
      for (double dx = -offset; dx < width; dx += period) {
        UIUtil.drawImage(g, image, 0, 0, null);
        g.translate(period, 0);
      }
      g.dispose();
    }
  }
}
