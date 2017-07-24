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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 * @author Sergey Malenkov
 */
public class MacIntelliJTextFieldUI extends TextFieldWithPopupHandlerUI {

  public MacIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new MacIntelliJTextFieldUI((JTextField)c);
  }

  @Override
  protected Icon getSearchIcon(boolean hovered, boolean clickable) {
    return MacIntelliJIconCache.getIcon(clickable ? "searchFieldWithHistory" : "search");
  }

  @Override
  protected Icon getClearIcon(boolean hovered, boolean clickable) {
    return !clickable ? null : MacIntelliJIconCache.getIcon("searchFieldClear");
  }

  @Override
  protected void updatePreferredSize(Dimension size) {
    super.updatePreferredSize(size);
    int height = JBUI.scale(28);
    if (size.height < height) size.height = height;
  }

  @Override
  protected int getClearIconPreferredSpace() {
    return super.getClearIconPreferredSpace() - getIconGap();
  }

  @Override
  protected void updateIconsLayout(Rectangle bounds) {
    super.updateIconsLayout(bounds);
    JTextComponent component = getComponent();
    if (component == null || component.hasFocus()) return;
    IconHolder clear = icons.get("clear");
    if (clear == null || clear.icon != null) return;
    IconHolder search = icons.get("search");
    if (search == null || search.icon == null || null != search.getAction()) return;
    search.bounds.x = bounds.x + bounds.width / 2;
  }

  @Override
  protected void paintBackground(Graphics g) {
    JTextComponent component = getComponent();
    if (component != null) {
      Container parent = component.getParent();
      if (parent != null && component.isOpaque()) {
        g.setColor(parent.getBackground());
        g.fillRect(0, 0, component.getWidth(), component.getHeight());
      }
      if (isSearchField(component)) {
        paintSearchField((Graphics2D)g, component, new Rectangle(component.getWidth(), component.getHeight()));
      }
      else if (component.getBorder() instanceof MacIntelliJTextBorder) {
        g.setColor(component.getBackground());
        g.fillRect(JBUI.scale(3), JBUI.scale(3), component.getWidth() - JBUI.scale(6), component.getHeight() - JBUI.scale(6));
      }
      else {
        super.paintBackground(g);
      }
    }
  }

  private static void paintSearchField(Graphics2D g, JTextComponent c, Rectangle r) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      int arc = JBUI.scale(6);
      double lw = UIUtil.isRetina(g2) ? 0.5 : 1.0;
      Shape outerShape = new RoundRectangle2D.Double(JBUI.scale(3), JBUI.scale(3),
                                                     r.width - JBUI.scale(6),
                                                     r.height - JBUI.scale(6),
                                                     arc, arc);
      g2.setColor(c.getBackground());
      g2.fill(outerShape);

      Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Double(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                              r.width - JBUI.scale(6) - lw*2,
                                              r.height - JBUI.scale(6) - lw*2,
                                              arc-lw, arc-lw), false);

      g2.setColor(Gray.xBC);
      g2.fill(path);

      if (c.hasFocus() && c.getClientProperty("JTextField.Search.noBorderRing") != Boolean.TRUE) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
      }

      g2.translate(-r.x, -r.y);
    } finally {
      g2.dispose();
    }
  }

  public static void paintAquaSearchFocusRing(Graphics2D g, Rectangle r, Component c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      int arc = JBUI.scale(6);
      double lw = UIUtil.isRetina(g2) ? 0.5 : 1.0;
      Shape outerShape = new RoundRectangle2D.Double(JBUI.scale(3), JBUI.scale(3),
                                                     r.width - JBUI.scale(6),
                                                     r.height - JBUI.scale(6),
                                                     arc, arc);
      g2.setColor(c.getBackground());
      g2.fill(outerShape);

      Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Double(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                              r.width - JBUI.scale(6) - lw*2,
                                              r.height - JBUI.scale(6) - lw*2,
                                              arc-lw, arc-lw), false);

      g2.setColor(Gray.xBC);
      g2.fill(path);

      if (c.hasFocus()) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
      }
    }
    finally {
      g2.dispose();
    }
  }
}
