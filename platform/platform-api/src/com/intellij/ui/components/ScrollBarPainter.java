// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.MixedColorProducer;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.RegionPainter;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JScrollPane;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.ui.components.DefaultScrollBarUI.isOpaque;

abstract class ScrollBarPainter implements RegionPainter<Float> {
  final Rectangle bounds = new Rectangle();
  final TwoWayAnimator animator;

  private static final ColorKey TRACK_OPAQUE_FOREGROUND
    = key(0xFFE6E6E6, 0xFF3F4244, 0xFFE6E6E6, 0xFF3F4244, "ScrollBar.foreground");
  private static final ColorKey TRACK_OPAQUE_BACKGROUND
    = key(0xFFF5F5F5, 0xFF3F4244, 0xFFF5F5F5, 0xFF3F4244, "ScrollBar.background");
  private static final ColorKey TRACK_BACKGROUND
    = key(0x00808080, 0x00808080, 0x00808080, 0x00808080, "ScrollBar.NonOpaque.background");
  private static final ColorKey TRACK_HOVERED_BACKGROUND
    = key(0x1A808080, 0x1A808080, 0x1A808080, 0x1A808080, "ScrollBar.NonOpaque.Hovered.background");

  private static final ColorKey THUMB_OPAQUE_FOREGROUND
    = key(0x33595959, 0x47383838, 0x33000000, 0x59262626, "ScrollBar.Thumb.foreground");
  private static final ColorKey THUMB_OPAQUE_BACKGROUND
    = key(0x33737373, 0x47A6A6A6, 0x33000000, 0x59808080, "ScrollBar.Thumb.background");
  private static final ColorKey THUMB_OPAQUE_HOVERED_FOREGROUND
    = key(0x47595959, 0x59383838, 0x80000000, 0x8C262626, "ScrollBar.Thumb.Hovered.foreground");
  private static final ColorKey THUMB_OPAQUE_HOVERED_BACKGROUND
    = key(0x47737373, 0x59A6A6A6, 0x80000000, 0x8C808080, "ScrollBar.Thumb.Hovered.background");
  private static final ColorKey THUMB_FOREGROUND
    = key(0x33595959, 0x47383838, 0x00000000, 0x00262626, "ScrollBar.Thumb.NonOpaque.foreground");
  private static final ColorKey THUMB_BACKGROUND
    = key(0x33737373, 0x47A6A6A6, 0x00000000, 0x00808080, "ScrollBar.Thumb.NonOpaque.background");
  private static final ColorKey THUMB_HOVERED_FOREGROUND
    = key(0x47595959, 0x59383838, 0x80000000, 0x8C262626, "ScrollBar.Thumb.NonOpaque.Hovered.foreground");
  private static final ColorKey THUMB_HOVERED_BACKGROUND
    = key(0x47737373, 0x59A6A6A6, 0x80000000, 0x8C808080, "ScrollBar.Thumb.NonOpaque.Hovered.background");

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
  private static ColorKey key(int light, int dark, int lightMac, int darkMac, @NotNull String name) {
    return ColorKey.createColorKey(name, JBColor.namedColor(name, new JBColor(
      new Color(!isMac ? light : lightMac, true),
      new Color(!isMac ? dark : darkMac, true))));
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

  static void setForeground(@NotNull Component component) {
    component.setForeground(new JBColor(() -> getColor(component, TRACK_OPAQUE_FOREGROUND)));
  }

  static void setBackground(@NotNull Component component) {
    component.setBackground(new JBColor(new NotNullProducer<Color>() {
      private Color original;
      private Color modified;

      @NotNull
      @Override
      public Color produce() {
        Container parent = component.getParent();
        if (parent instanceof JScrollPane && ScrollSettings.isBackgroundFromView()) {
          Color background = JBScrollPane.getViewBackground((JScrollPane)parent);
          if (background != null) {
            if (!background.equals(original)) {
              modified = ColorUtil.shift(background, ColorUtil.isDark(background) ? 1.05 : 0.96);
              original = background;
            }
            return modified;
          }
        }
        return getColor(component, TRACK_OPAQUE_BACKGROUND);
      }
    }));
  }

  static final class Track extends ScrollBarPainter {
    private final MixedColorProducer fillProducer;

    Track(@NotNull Supplier<? extends Component> supplier) {
      super(supplier);
      fillProducer = new MixedColorProducer(
        getColor(supplier, TRACK_BACKGROUND),
        getColor(supplier, TRACK_HOVERED_BACKGROUND));
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
