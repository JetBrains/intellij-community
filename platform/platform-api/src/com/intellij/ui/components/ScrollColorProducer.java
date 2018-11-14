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

import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.function.Function;
import javax.swing.JScrollPane;

import static com.intellij.ui.components.JBScrollPane.BRIGHTNESS_FROM_VIEW;

/**
 * @author Sergey.Malenkov
 */
final class ScrollColorProducer {
  static final ColorKey THUMB_OPAQUE_FOREGROUND = createKey("ScrollBar.Thumb.foreground", 0x33000000, 0x59262626);
  static final ColorKey THUMB_OPAQUE_BACKGROUND = createKey("ScrollBar.Thumb.background", 0x33000000, 0x59808080);
  static final ColorKey THUMB_OPAQUE_HOVERED_FOREGROUND = createKey("ScrollBar.Thumb.Hovered.foreground", 0x80000000, 0x8C262626);
  static final ColorKey THUMB_OPAQUE_HOVERED_BACKGROUND = createKey("ScrollBar.Thumb.Hovered.background", 0x80000000, 0x8C808080);
  static final ColorKey THUMB_FOREGROUND = createKey("ScrollBar.Thumb.NonOpaque.foreground", 0x00000000, 0x00262626);
  static final ColorKey THUMB_BACKGROUND = createKey("ScrollBar.Thumb.NonOpaque.background", 0x00000000, 0x00808080);
  static final ColorKey THUMB_HOVERED_FOREGROUND = createKey("ScrollBar.Thumb.NonOpaque.Hovered.foreground", 0x80000000, 0x8C262626);
  static final ColorKey THUMB_HOVERED_BACKGROUND = createKey("ScrollBar.Thumb.NonOpaque.Hovered.background", 0x80000000, 0x8C808080);
  static final ColorKey TRACK_HOVERED_BACKGROUND = createKey("ScrollBar.NonOpaque.Hovered.background", 0x1A808080, 0x1A808080);

  private static final ColorKey FOREGROUND = createKey("ScrollBar.foreground", 0xFFE6E6E6, 0xFF3F4244);
  private static final ColorKey BACKGROUND = createKey("ScrollBar.background", 0xFFF5F5F5, 0xFF3F4244);

  @NotNull
  private static ColorKey createKey(@NotNull String name, int light, int dark) {
    return ColorKey.createColorKey(name, JBColor.namedColor(name, new JBColor(new Color(light, true), new Color(dark, true))));
  }

  @NotNull
  static Color getColor(@NotNull ColorKey key, Component component) {
    Function<ColorKey, Color> function = UIUtil.getClientProperty(component, ColorKey.FUNCTION_KEY);
    Color color = function == null ? null : function.apply(key);
    return color != null ? color : key.getDefaultColor();
  }

  static void setForeground(@NotNull Component component) {
    component.setForeground(new JBColor(() -> getColor(FOREGROUND, component)));
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
        return getColor(BACKGROUND, component);
      }
    }));
  }

  static boolean isDark(Component c) {
    Container parent = c.getParent();
    if (parent instanceof JScrollPane) {
      JScrollPane pane = (JScrollPane)parent;
      Object property = pane.getClientProperty(BRIGHTNESS_FROM_VIEW);
      if (property instanceof Boolean && (Boolean)property) {
        Color color = JBScrollPane.getViewBackground(pane);
        if (color != null) return ColorUtil.isDark(color);
      }
    }
    return UIUtil.isUnderDarcula();
  }
}
