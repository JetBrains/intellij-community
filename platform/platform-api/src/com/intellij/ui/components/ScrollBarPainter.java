// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.JBColor;
import com.intellij.ui.MixedColorProducer;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.ui.components.DefaultScrollBarUI.isOpaque;

abstract class ScrollBarPainter implements RegionPainter<Float> {
  final Rectangle bounds = new Rectangle();
  final TwoWayAnimator animator;

  private static final ColorKey BACKGROUND = key(0xFFF5F5F5, 0xFF3F4244, "ScrollBar.background");

  private static final ColorKey TRACK_OPAQUE_BACKGROUND
    = isMac ? key(0x00808080, 0x00808080, "ScrollBar.Mac.trackColor")
            : key(0x00808080, 0x00808080, "ScrollBar.trackColor");
  private static final ColorKey TRACK_OPAQUE_HOVERED_BACKGROUND
    = isMac ? key(0x00808080, 0x00808080, "ScrollBar.Mac.hoverTrackColor")
            : key(0x00808080, 0x00808080, "ScrollBar.hoverTrackColor");
  private static final ColorKey TRACK_BACKGROUND
    = isMac ? key(0x00808080, 0x00808080, "ScrollBar.Mac.Transparent.trackColor")
            : key(0x00808080, 0x00808080, "ScrollBar.Transparent.trackColor");
  private static final ColorKey TRACK_HOVERED_BACKGROUND
    = isMac ? key(0x1A808080, 0x1A808080, "ScrollBar.Mac.Transparent.hoverTrackColor")
            : key(0x1A808080, 0x1A808080, "ScrollBar.Transparent.hoverTrackColor");

  private static final ColorKey THUMB_OPAQUE_FOREGROUND
    = isMac ? key(0x33000000, 0x59262626, "ScrollBar.Mac.thumbBorderColor")
            : key(0x33595959, 0x47383838, "ScrollBar.thumbBorderColor");
  private static final ColorKey THUMB_OPAQUE_BACKGROUND
    = isMac ? key(0x33000000, 0x59808080, "ScrollBar.Mac.thumbColor")
            : key(0x33737373, 0x47A6A6A6, "ScrollBar.thumbColor");
  private static final ColorKey THUMB_OPAQUE_HOVERED_FOREGROUND
    = isMac ? key(0x80000000, 0x8C262626, "ScrollBar.Mac.hoverThumbBorderColor")
            : key(0x47595959, 0x59383838, "ScrollBar.hoverThumbBorderColor");
  private static final ColorKey THUMB_OPAQUE_HOVERED_BACKGROUND
    = isMac ? key(0x80000000, 0x8C808080, "ScrollBar.Mac.hoverThumbColor")
            : key(0x47737373, 0x59A6A6A6, "ScrollBar.hoverThumbColor");
  private static final ColorKey THUMB_FOREGROUND
    = isMac ? key(0x00000000, 0x00262626, "ScrollBar.Mac.Transparent.thumbBorderColor")
            : key(0x33595959, 0x47383838, "ScrollBar.Transparent.thumbBorderColor");
  private static final ColorKey THUMB_BACKGROUND
    = isMac ? key(0x00000000, 0x00808080, "ScrollBar.Mac.Transparent.thumbColor")
            : key(0x33737373, 0x47A6A6A6, "ScrollBar.Transparent.thumbColor");
  private static final ColorKey THUMB_HOVERED_FOREGROUND
    = isMac ? key(0x80000000, 0x8C262626, "ScrollBar.Mac.Transparent.hoverThumbBorderColor")
            : key(0x47595959, 0x59383838, "ScrollBar.Transparent.hoverThumbBorderColor");
  private static final ColorKey THUMB_HOVERED_BACKGROUND
    = isMac ? key(0x80000000, 0x8C808080, "ScrollBar.Mac.Transparent.hoverThumbColor")
            : key(0x47737373, 0x59A6A6A6, "ScrollBar.Transparent.hoverThumbColor");

  protected ScrollBarPainter(@NotNull Supplier<? extends Component> supplier) {
    animator = new TwoWayAnimator(getClass().getName(), 11, 150, 125, 300, 125) {
      @Override
      void onValueUpdate() {
        Component component = supplier.get();
        if (component != null) component.repaint();
      }
    };
  }

  @NotNull
  private static ColorKey key(int light, int dark, @NotNull String name) {
    return ColorKey.createColorKey(name, JBColor.namedColor(name, new JBColor(new Color(light, true), new Color(dark, true))));
  }

  @NotNull
  static Color getColor(@Nullable Component component, @NotNull ColorKey key) {
    Function<ColorKey, Color> function = UIUtil.getClientProperty(component, ColorKey.FUNCTION_KEY);
    Color color = function == null ? null : function.apply(key);
    return color != null ? color : key.getDefaultColor();
  }

  static Color getColor(@NotNull Supplier<? extends Component> supplier, @NotNull ColorKey key) {
    return new JBColor(() -> getColor(supplier.get(), key));
  }

  static Color getColor(@NotNull Supplier<? extends Component> supplier, @NotNull ColorKey transparent, @NotNull ColorKey opaque) {
    return new JBColor(() -> {
      Component component = supplier.get();
      return getColor(component, component != null && isOpaque(component) ? opaque : transparent);
    });
  }

  static void setBackground(@NotNull Component component) {
    component.setBackground(new JBColor(() -> getColor(component, BACKGROUND)));
  }

  static final class Track extends ScrollBarPainter {
    private final MixedColorProducer fillProducer;

    Track(@NotNull Supplier<? extends Component> supplier) {
      super(supplier);
      fillProducer = new MixedColorProducer(
        getColor(supplier, TRACK_BACKGROUND, TRACK_OPAQUE_BACKGROUND),
        getColor(supplier, TRACK_HOVERED_BACKGROUND, TRACK_OPAQUE_HOVERED_BACKGROUND));
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Float value) {
      double mixer = value == null ? 0 : value.doubleValue();
      Color fill = fillProducer.produce(mixer);
      if (0 >= fill.getAlpha()) return; // optimization

      g.setPaint(fill);
      RectanglePainter.FILL.paint(g, x, y, width, height, null);
    }
  }

  static final class Thumb extends ScrollBarPainter {
    private final MixedColorProducer fillProducer;
    private final MixedColorProducer drawProducer;

    Thumb(@NotNull Supplier<? extends Component> supplier, boolean opaque) {
      super(supplier);
      fillProducer = new MixedColorProducer(
        opaque ? getColor(supplier, THUMB_OPAQUE_BACKGROUND)
               : getColor(supplier, THUMB_BACKGROUND, THUMB_OPAQUE_BACKGROUND),
        opaque ? getColor(supplier, THUMB_OPAQUE_HOVERED_BACKGROUND)
               : getColor(supplier, THUMB_HOVERED_BACKGROUND, THUMB_OPAQUE_HOVERED_BACKGROUND));
      drawProducer = new MixedColorProducer(
        opaque ? getColor(supplier, THUMB_OPAQUE_FOREGROUND)
               : getColor(supplier, THUMB_FOREGROUND, THUMB_OPAQUE_FOREGROUND),
        opaque ? getColor(supplier, THUMB_OPAQUE_HOVERED_FOREGROUND)
               : getColor(supplier, THUMB_HOVERED_FOREGROUND, THUMB_OPAQUE_HOVERED_FOREGROUND));
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Float value) {
      double mixer = value == null ? 0 : value.doubleValue();
      Color fill = fillProducer.produce(mixer);
      Color draw = drawProducer.produce(mixer);
      if (fill.getRGB() == draw.getRGB()) draw = null; // without border

      int arc = 0;
      if (isMac) {
        int margin = draw == null ? 2 : 1;
        x += margin;
        y += margin;
        width -= margin + margin;
        height -= margin + margin;
        arc = Math.min(width, height);
      }
      RectanglePainter.paint(g, x, y, width, height, arc, fill, draw);
    }
  }
}
