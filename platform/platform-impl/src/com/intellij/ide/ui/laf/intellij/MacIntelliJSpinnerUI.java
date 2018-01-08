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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import java.awt.*;
import java.awt.geom.Path2D;


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
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    Insets i = c.getInsets();
    int x = c.getWidth() - DEFAULT_ICON.getIconWidth() - i.right;

    if (c instanceof JSpinner) {
      Graphics2D g2 = (Graphics2D)g;
      g2.setColor(getBackground());

      float arc = JBUI.scale(6f);
      Path2D rect = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      rect.moveTo(x, i.top);
      rect.lineTo(x, c.getHeight() - i.bottom);
      rect.lineTo(i.left + arc, c.getHeight() - i.bottom);
      rect.quadTo(i.left, c.getHeight() - i.bottom, i.left, c.getHeight() - i.bottom - arc);
      rect.lineTo(i.left, i.top + arc);
      rect.quadTo(i.left, i.top, i.left + arc, i.top);
      rect.closePath();

      g2.fill(rect);
    }

    Icon icon = MacIntelliJIconCache.getIcon("spinnerRight", false, false, c.isEnabled());
    icon.paintIcon(c, g, x, i.top);
  }

  @Override protected void paintArrowButton(Graphics g, BasicArrowButton button, int direction) {}

  @Override public Dimension getPreferredSize(JComponent c) {
    return getSizeWithIcon(super.getPreferredSize(c));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return getSizeWithIcon(super.getMinimumSize(c));
  }

  private Dimension getSizeWithIcon(Dimension d) {
    if (d == null) return null;

    Insets i = spinner.getInsets();
    int iconWidth = DEFAULT_ICON.getIconWidth() + i.right;
    int iconHeight = DEFAULT_ICON.getIconHeight() + i.top + i.bottom;
    return new Dimension(Math.max(d.width + JBUI.scale(7), iconWidth), Math.max(d.height, iconHeight));
  }

  @Override
  protected LayoutManager createLayout() {
    return new LayoutManagerDelegate(super.createLayout()) {
      @Override
      public Dimension preferredLayoutSize(Container parent) {
        Dimension d = super.preferredLayoutSize(parent);
        if (d == null) return null;

        Insets i = parent.getInsets();
        return new Dimension(Math.max(DEFAULT_ICON.getIconWidth() + JBUI.scale(25) + i.left + i.right, d.width),
                             DEFAULT_ICON.getIconHeight() + i.top + i.bottom);
      }

      @Override
      public Dimension minimumLayoutSize(Container parent) {
        Dimension d = super.minimumLayoutSize(parent);
        if (d == null) return null;

        Insets i = parent.getInsets();
        return new Dimension(Math.max(DEFAULT_ICON.getIconWidth() + JBUI.scale(10) + i.left + i.right, d.width),
                             DEFAULT_ICON.getIconHeight() + i.top + i.bottom);
      }
    };
  }

  @Override
  protected void layout() {
    JComponent editor = spinner.getEditor();
    if (editor != null) {
      int w = spinner.getWidth();
      int h = spinner.getHeight();
      Insets i = spinner.getInsets();
      editor.setBounds(JBUI.scale(2) + i.left,
                       JBUI.scale(2) + i.top,
                       w - (i.left + i.right + DEFAULT_ICON.getIconWidth() + JBUI.scale(6)),
                       h - (i.top + i.bottom + JBUI.scale(2) * 2));
    }
  }

  @Nullable Rectangle getArrowButtonBounds() {
    Insets i = spinner.getInsets();
    return new Rectangle(spinner.getWidth() - DEFAULT_ICON.getIconWidth() - i.right,
                         i.top,
                         DEFAULT_ICON.getIconWidth(),
                         DEFAULT_ICON.getIconHeight());
  }
}
