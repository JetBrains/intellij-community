/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.RegionPainter;

import java.awt.*;
import java.awt.image.*;

/**
 * @author Sergey.Malenkov
 */
class ScrollPainter extends RegionPainter.Alpha {

  static final class Track {
    static final RegionPainter<Float> DARCULA = new ScrollPainter(0, .0f, .1f, Gray.x80, null);
    static final RegionPainter<Float> DEFAULT = DARCULA;
  }

  static final class Thumb {
    static final RegionPainter<Float> DARCULA = new ScrollPainter(0, .28f, .07f, Gray.xA6, Gray.x38);
    static final RegionPainter<Float> DEFAULT = new Protected(new SubtractColor(0, .20f, .08f, Gray.x73, Gray.x91),
                                                              new ScrollPainter(0, .20f, .08f, Gray.x73, Gray.x59));

    static final class Mac {
      static final RegionPainter<Float> DARCULA = new Round(1, .35f, .20f, Gray.x80, Gray.x26);
      static final RegionPainter<Float> DEFAULT = new Round(2, .20f, .30f, Gray.x00, null);

      static final class Overlay {
        static final RegionPainter<Float> DARCULA = new Round(1, 0f, .55f, Gray.x80, Gray.x26);
        static final RegionPainter<Float> DEFAULT = new Round(2, 0f, .50f, Gray.x00, null);
      }
    }
  }

  static final class EditorThumb {
    static final RegionPainter<Float> DARCULA = new ScrollPainter(0, .33f, .12f, Gray.xA6, Gray.x1A);
    static final RegionPainter<Float> DEFAULT = new Protected(new SubtractColor(0, .25f, .15f, Gray.x80, Gray.xA6),
                                                              new ScrollPainter(0, .25f, .15f, Gray.x80, Gray.x59));

    static final class Mac {
      static final RegionPainter<Float> DARCULA = new Round(1, .35f, .20f, Gray.xA6, Gray.x0D);
      static final RegionPainter<Float> DEFAULT = Thumb.Mac.DEFAULT;

      static final class Overlay {
        static final RegionPainter<Float> DARCULA = new Round(1, 0f, .55f, Gray.xA6, Gray.x0D);
        static final RegionPainter<Float> DEFAULT = Thumb.Mac.Overlay.DEFAULT;
      }
    }
  }

  private final int myOffset;
  private final float myAlphaBase;
  private final float myAlphaDelta;
  private final Color myFillColor;
  private final Color myDrawColor;

  private ScrollPainter(int offset, float base, float delta, Color fill, Color draw) {
    myOffset = offset;
    myAlphaBase = base;
    myAlphaDelta = delta;
    myFillColor = fill;
    myDrawColor = draw;
  }

  @Override
  protected float getAlpha(Float value) {
    return value != null ? myAlphaBase + myAlphaDelta * value : 0;
  }

  @Override
  protected void paint(Graphics2D g, int x, int y, int width, int height) {
    if (myOffset > 0) {
      x += myOffset;
      y += myOffset;
      width -= myOffset + myOffset;
      height -= myOffset + myOffset;
    }
    if (width > 0 && height > 0) {
      if (myFillColor != null) {
        g.setColor(myFillColor);
        fill(g, x, y, width, height, myDrawColor != null);
      }
      if (myDrawColor != null) {
        g.setColor(myDrawColor);
        draw(g, x, y, width, height);
      }
    }
  }

  protected void fill(Graphics2D g, int x, int y, int width, int height, boolean border) {
    if (border) {
      g.fillRect(x + 1, y + 1, width - 2, height - 2);
    }
    else {
      g.fillRect(x, y, width, height);
    }
  }

  protected void draw(Graphics2D g, int x, int y, int width, int height) {
    if (Registry.is("ide.scroll.thumb.border.rounded")) {
      g.drawLine(x + 1, y, x + width - 2, y);
      g.drawLine(x + 1, y + height - 1, x + width - 2, y + height - 1);
      g.drawLine(x, y + 1, x, y + height - 2);
      g.drawLine(x + width - 1, y + 1, x + width - 1, y + height - 2);
    }
    else {
      g.drawRect(x, y, width - 1, height - 1);
    }
  }

  private static class Round extends ScrollPainter {
    private Round(int offset, float base, float delta, Color fill, Color draw) {
      super(offset, base, delta, fill, draw);
    }

    @Override
    protected void fill(Graphics2D g, int x, int y, int width, int height, boolean border) {
      RectanglePainter.FILL.paint(g, x, y, width, height, Math.min(width, height));
    }

    @Override
    protected void draw(Graphics2D g, int x, int y, int width, int height) {
      RectanglePainter.DRAW.paint(g, x, y, width, height, Math.min(width, height));
    }
  }

  private static class Protected implements RegionPainter<Float> {
    private RegionPainter<Float> myPainter;
    private RegionPainter<Float> myFallback;

    private Protected(RegionPainter<Float> painter, RegionPainter<Float> fallback) {
      myPainter = painter;
      myFallback = fallback;
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
      RegionPainter<Float> painter = myFallback;
      if (myPainter != null) {
        try {
          myPainter.paint(g, x, y, width, height, value);
          return;
        }
        catch (Throwable exception) {
          // do not try to use myPainter again on other systems
          if (!SystemInfo.isWindows) myPainter = null;
        }
      }
      if (painter != null) {
        painter.paint(g, x, y, width, height, value);
      }
    }
  }

  private static class SubtractColor extends ScrollPainter {
    private SubtractColor(int offset, float base, float delta, Color fill, Color draw) {
      super(offset, base, delta, fill, draw);
    }

    @Override
    protected Composite getComposite(float alpha) {
      return alpha < 1
             ? new SubtractComposite(alpha)
             : AlphaComposite.SrcOver;
    }
  }

  private static class SubtractComposite implements Composite, CompositeContext {
    private final float myAlpha;

    private SubtractComposite(float alpha) {
      myAlpha = alpha;
    }

    private int subtract(int newValue, int oldValue) {
      float value = (oldValue & 0xFF) - (newValue & 0xFF) * myAlpha;
      return value <= 0 ? 0 : (int)value;
    }

    @Override
    public CompositeContext createContext(ColorModel src, ColorModel dst, RenderingHints hints) {
      return isValid(src) && isValid(dst) ? this : AlphaComposite.SrcOver.derive(myAlpha).createContext(src, dst, hints);
    }

    private static boolean isValid(ColorModel model) {
      if (model instanceof DirectColorModel && DataBuffer.TYPE_INT == model.getTransferType()) {
        DirectColorModel dcm = (DirectColorModel)model;
        if (0x00FF0000 == dcm.getRedMask() && 0x0000FF00 == dcm.getGreenMask() && 0x000000FF == dcm.getBlueMask()) {
          return 4 != dcm.getNumComponents() || 0xFF000000 == dcm.getAlphaMask();
        }
      }
      return false;
    }

    @Override
    public void compose(Raster srcIn, Raster dstIn, WritableRaster dstOut) {
      int width = Math.min(srcIn.getWidth(), dstIn.getWidth());
      int height = Math.min(srcIn.getHeight(), dstIn.getHeight());

      int[] srcPixels = new int[width];
      int[] dstPixels = new int[width];

      for (int y = 0; y < height; y++) {
        srcIn.getDataElements(0, y, width, 1, srcPixels);
        dstIn.getDataElements(0, y, width, 1, dstPixels);
        for (int x = 0; x < width; x++) {
          int src = srcPixels[x];
          int dst = dstPixels[x];
          int a = subtract(src >> 24, dst >> 24) << 24;
          int r = subtract(src >> 16, dst >> 16) << 16;
          int g = subtract(src >> 8, dst >> 8) << 8;
          int b = subtract(src, dst);
          dstPixels[x] = a | r | g | b;
        }
        dstOut.setDataElements(0, y, width, 1, dstPixels);
      }
    }

    @Override
    public void dispose() {
    }
  }
}
