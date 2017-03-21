/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaSpinnerUI;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJSpinnerUI extends DarculaSpinnerUI {
  private static final Icon DEFAULT_ICON = EmptyIcon.create(MacIntelliJIconCache.getIcon("spinnerRight"));

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJSpinnerUI();
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Container parent = c.getParent();
    if (c.isOpaque() && parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0,0,c.getWidth(),c.getHeight());
    }

    Insets i = c.getInsets();

    int x = c.getWidth() - DEFAULT_ICON.getIconWidth() - i.right;
    Icon icon = MacIntelliJIconCache.getIcon("spinnerRight", false, false, c.isEnabled());
    icon.paintIcon(c, g, x, i.top);
  }

  @Override protected void paintArrowButton(Graphics g, BasicArrowButton button, int direction) {}

  @Override
  protected LayoutManager createLayout() {
    return new LayoutManagerDelegate(super.createLayout()) {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Insets i = parent.getInsets();
        return new Dimension(DEFAULT_ICON.getIconWidth() + JBUI.scale(20) + i.left + i.right,
                             DEFAULT_ICON.getIconHeight() + i.top + i.bottom);
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        Insets i = parent.getInsets();
        return new Dimension(DEFAULT_ICON.getIconWidth() + JBUI.scale(10) + i.left + i.right,
                             DEFAULT_ICON.getIconHeight() + i.top + i.bottom);
      }
    };
  }

  @Override
  protected void layoutEditor(@NotNull JComponent editor) {
    int w = spinner.getWidth();
    int h = spinner.getHeight();
    Insets i = spinner.getInsets();
    editor.setBounds(JBUI.scale(1) + i.left,
                     JBUI.scale(1) + i.top,
                     w - (i.left + i.right + DEFAULT_ICON.getIconWidth() + JBUI.scale(1)),
                     h - (i.top + i.bottom + + JBUI.scale(2)));
  }

  @Nullable Rectangle getArrowButtonBounds() {
    Insets i = spinner.getInsets();
    return new Rectangle(spinner.getWidth() - DEFAULT_ICON.getIconWidth() - i.right,
                         i.top,
                         DEFAULT_ICON.getIconWidth(),
                         DEFAULT_ICON.getIconHeight());
  }
}
