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
  static final ColorKey FOREGROUND = key("ScrollBar.foreground", 0xFFE6E6E6, 0xFF3F4244);
  static final ColorKey BACKGROUND = key("ScrollBar.background", 0xFFF5F5F5, 0xFF3F4244);
  final Rectangle bounds = new Rectangle();
  final TwoWayAnimator animator;

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
  private static ColorKey key(@NotNull String name, int light, int dark) {
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

  static final class Track extends ScrollBarPainter {
    private static final ColorKey TRACK_BACKGROUND = key("ScrollBar.NonOpaque.background", 0x00808080, 0x00808080);
    private static final ColorKey TRACK_HOVERED_BACKGROUND = key("ScrollBar.NonOpaque.Hovered.background", 0x1A808080, 0x1A808080);
    private final MixedColorProducer fillProducer;

    Track(@NotNull Supplier<? extends Component> supplier) {
      super(supplier);
      fillProducer = new MixedColorProducer(
        getColor(supplier, TRACK_BACKGROUND),
        getColor(supplier, TRACK_HOVERED_BACKGROUND));
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Float value) {
      Color fill = fillProducer.produce(animator.myValue);
      if (0 >= fill.getAlpha()) return; // optimization

      g.setPaint(fill);
      RectanglePainter.FILL.paint(g, x, y, width, height, null);
    }
  }

  static final class Thumb extends ScrollBarPainter {
    private static final ColorKey THUMB_OPAQUE_FOREGROUND = key("ScrollBar.Thumb.foreground",
                                                                0x33000000, isMac ? 0x59262626 : 0x47383838);
    private static final ColorKey THUMB_OPAQUE_BACKGROUND = key("ScrollBar.Thumb.background",
                                                                0x33000000, isMac ? 0x59808080 : 0x47A6A6A6);
    private static final ColorKey THUMB_OPAQUE_HOVERED_FOREGROUND = key("ScrollBar.Thumb.Hovered.foreground",
                                                                        0x80000000, isMac ? 0x8C262626 : 0x59383838);
    private static final ColorKey THUMB_OPAQUE_HOVERED_BACKGROUND = key("ScrollBar.Thumb.Hovered.background",
                                                                        0x80000000, isMac ? 0x8C808080 : 0x59A6A6A6);
    private static final ColorKey THUMB_FOREGROUND = key("ScrollBar.Thumb.NonOpaque.foreground",
                                                         0x00000000, isMac ? 0x00262626 : 0x47383838);
    private static final ColorKey THUMB_BACKGROUND = key("ScrollBar.Thumb.NonOpaque.background",
                                                         0x00000000, isMac ? 0x00808080 : 0x47A6A6A6);
    private static final ColorKey THUMB_HOVERED_FOREGROUND = key("ScrollBar.Thumb.NonOpaque.Hovered.foreground",
                                                                 0x80000000, isMac ? 0x8C262626 : 0x59383838);
    private static final ColorKey THUMB_HOVERED_BACKGROUND = key("ScrollBar.Thumb.NonOpaque.Hovered.background",
                                                                 0x80000000, isMac ? 0x8C808080 : 0x59A6A6A6);
    private final MixedColorProducer fillProducer;
    private final MixedColorProducer drawProducer;

    Thumb(@NotNull Supplier<? extends Component> supplier) {
      super(supplier);
      fillProducer = new MixedColorProducer(
        getColor(supplier, THUMB_BACKGROUND, THUMB_OPAQUE_BACKGROUND),
        getColor(supplier, THUMB_HOVERED_BACKGROUND, THUMB_OPAQUE_HOVERED_BACKGROUND));
      drawProducer = new MixedColorProducer(
        getColor(supplier, THUMB_FOREGROUND, THUMB_OPAQUE_FOREGROUND),
        getColor(supplier, THUMB_HOVERED_FOREGROUND, THUMB_OPAQUE_HOVERED_FOREGROUND));
    }

    @Override
    public void paint(Graphics2D g, int x, int y, int width, int height, @Nullable Float value) {
      Color fill = fillProducer.produce(animator.myValue);
      Color draw = drawProducer.produce(animator.myValue);
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
