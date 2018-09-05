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
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.RegionPainter;

import java.awt.*;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * @author Sergey.Malenkov
 */
class ScrollPainter extends RegionPainter.Alpha {
  private static final Color x0D = gray("mac.scroll.thumb.darcula.border", 0x0D);
  private static final Color xA6 = gray("mac.scroll.thumb.darcula.fill", 0xA6);

  private static Color gray(String key, int defaultValue) {
    return new JBColor(() -> {
      int value = Registry.intValue(key, defaultValue);
      return value <= 0 ? Gray._0 : value >= 255 ? Gray._255 : Gray.get(value);
    });
  }

  private static IntSupplier value(String key, int defaultValue) {
    return () -> {
      int value = Registry.intValue(key, defaultValue);
      return value <= 0 ? 0 : value >= 255 ? 255 : value;
    };
  }

  static final class Track {
    static final RegionPainter<Float> DARCULA = new TrackPainter("ide.scroll.track.darcula.alpha", "ide.scroll.track.darcula.fill");
    static final RegionPainter<Float> DEFAULT = new TrackPainter("ide.scroll.track.default.alpha", "ide.scroll.track.default.fill");
  }

  static final class Thumb {
    static final RegionPainter<Float> DARCULA = new ScrollPainter(0, .28f, .07f, Gray.xA6, Gray.x38);
    static final RegionPainter<Float> DEFAULT = new ScrollPainter(0, .20f, .08f, Gray.x73, Gray.x59);

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
    private static final RegionPainter<Float> DARCULA_OLD = new ScrollPainter(0, .33f, .12f, Gray.xA6, Gray.x1A);
    private static final RegionPainter<Float> DARCULA_NEW = new EditorThumbPainter(
      0,
      value("win.editor.thumb.darcula.alpha.base", 89),
      value("win.editor.thumb.darcula.alpha.delta", 166),
      new ColorFunction(
        value("win.editor.thumb.darcula.fill.min", 0x8C),
        value("win.editor.thumb.darcula.fill.max", 0xA1)),
      gray("win.editor.thumb.darcula.border", 0x1F));

    private static final RegionPainter<Float> DEFAULT_OLD = new ScrollPainter(0, .25f, .15f, Gray.x80, Gray.x59);
    private static final RegionPainter<Float> DEFAULT_NEW = new EditorThumbPainter(
      0,
      value("win.editor.thumb.default.alpha.base", 140),
      value("win.editor.thumb.default.alpha.delta", 115),
      new ColorFunction(
        value("win.editor.thumb.default.fill.min", 0x9E),
        value("win.editor.thumb.default.fill.max", 0xBD)),
      gray("win.editor.thumb.default.border", 0x8C));

    static final RegionPainter<Float> DARCULA = new RegionPainter<Float>() {
      @Override
      public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
        RegionPainter<Float> painter = Registry.is("ide.editor.thumb.experimental") ? DARCULA_NEW : DARCULA_OLD;
        painter.paint(g, x, y, width, height, value);
      }
    };
    static final RegionPainter<Float> DEFAULT = new RegionPainter<Float>() {
      @Override
      public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
        RegionPainter<Float> painter = Registry.is("ide.editor.thumb.experimental") ? DEFAULT_NEW : DEFAULT_OLD;
        painter.paint(g, x, y, width, height, value);
      }
    };

    static final class Mac {
      private static final RegionPainter<Float> DARCULA_OLD = new Round(1, .35f, .20f, xA6, x0D);
      private static final RegionPainter<Float> DARCULA_NEW = new EditorThumbPainter(
        1,
        value("mac.editor.thumb.darcula.alpha.base", 102),
        value("mac.editor.thumb.darcula.alpha.delta", 153),
        new ColorFunction(
          value("mac.editor.thumb.darcula.fill.min", 0x8C),
          value("mac.editor.thumb.darcula.fill.max", 0xA1)),
        gray("mac.editor.thumb.darcula.border", 0x1F));

      private static final RegionPainter<Float> DEFAULT_OLD = Thumb.Mac.DEFAULT;
      private static final RegionPainter<Float> DEFAULT_NEW = new EditorThumbPainter(
        2,
        value("mac.editor.thumb.default.alpha.base", 102),
        value("mac.editor.thumb.default.alpha.delta", 153),
        new ColorFunction(
          value("mac.editor.thumb.default.fill.min", 0x59),
          value("mac.editor.thumb.default.fill.max", 0x73)),
        null);

      static final RegionPainter<Float> DARCULA = new RegionPainter<Float>() {
        @Override
        public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
          RegionPainter<Float> painter = Registry.is("ide.editor.thumb.experimental") ? DARCULA_NEW : DARCULA_OLD;
          painter.paint(g, x, y, width, height, value);
        }
      };
      static final RegionPainter<Float> DEFAULT = new RegionPainter<Float>() {
        @Override
        public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
          RegionPainter<Float> painter = Registry.is("ide.editor.thumb.experimental") ? DEFAULT_NEW : DEFAULT_OLD;
          painter.paint(g, x, y, width, height, value);
        }
      };
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
    RectanglePainter.DRAW.paint(g, x, y, width, height, null);
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

  private static final class ColorFunction implements Function<Float, Color> {
    private final IntSupplier myMinSupplier;
    private final IntSupplier myMaxSupplier;

    private ColorFunction(IntSupplier min, IntSupplier max) {
      myMinSupplier = min;
      myMaxSupplier = max;
    }

    @Override
    public Color apply(Float value) {
      int min = myMinSupplier.getAsInt();
      if (value != null) {
        int max = myMaxSupplier.getAsInt();
        if (max != min) min += (int)(0.5 + value * (max - min));
      }
      //noinspection UseJBColor
      return new Color(min, min, min);
    }
  }

  private static final class EditorThumbPainter extends RegionPainter.Alpha {
    private final int myOffset;
    private final IntSupplier myAlphaBase;
    private final IntSupplier myAlphaDelta;
    private final Function<? super Float, ? extends Color> myFillFunction;
    private final Color myDrawColor;
    private Color myFillColor;

    private EditorThumbPainter(int offset, IntSupplier base, IntSupplier delta, Function<? super Float, ? extends Color> fill, Color draw) {
      myOffset = offset;
      myAlphaBase = base;
      myAlphaDelta = delta;
      myFillFunction = fill;
      myDrawColor = draw;
    }

    private float getAlpha(int width, int height) {
      if (width == height) return myAlphaBase.getAsInt() + myAlphaDelta.getAsInt();

      int size = Math.abs(width - height);
      float threshold = JBUI.scale(500f);
      if (threshold <= size) return myAlphaBase.getAsInt();

      float function = 1 - size / threshold;
      return myAlphaBase.getAsInt() + myAlphaDelta.getAsInt() * function * function;
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, Float value) {
      if (myOffset > 0) {
        x += myOffset;
        y += myOffset;
        width -= myOffset + myOffset;
        height -= myOffset + myOffset;
      }
      if (width > 0 && height > 0) {
        myFillColor = myFillFunction.apply(value);
        super.paint(g, x, y, width, height, getAlpha(width, height) / 255);
      }
    }

    @Override
    protected void paint(Graphics2D g, int x, int y, int width, int height) {
      RectanglePainter.paint(g, x, y, width, height, SystemInfo.isMac ? Math.min(width, height) : 0, myFillColor, myDrawColor);
    }
  }

  private static class TrackPainter extends ScrollPainter {
    private final IntSupplier supplier;

    private TrackPainter(String alpha, String fill) {
      super(0, 0, 0, gray(fill, 0x80), null);
      supplier = value(alpha, 26);
    }

    @Override
    protected float getAlpha(Float value) {
      return value == null ? 0 : value * supplier.getAsInt() / 255;
    }
  }
}
