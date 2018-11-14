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

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.NotNullProducer;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JScrollPane;

/**
 * @author Sergey.Malenkov
 */
final class ScrollColorProducer {
  static void setForeground(@NotNull Component component) {
    component.setForeground(new JBColor(() -> ScrollBarPainter.getColor(component, ScrollBarPainter.FOREGROUND)));
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
        return ScrollBarPainter.getColor(component, ScrollBarPainter.BACKGROUND);
      }
    }));
  }
}
