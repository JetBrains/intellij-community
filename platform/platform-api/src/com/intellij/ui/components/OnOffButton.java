/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class OnOffButton extends JToggleButton {
  private String myOnText;
  private String myOffText;
  public OnOffButton() {
    setUI(OnOffButtonUI.createUI(this));
    setOpaque(false);
    setBorder(null);
  }

  public String getOnText() {
    return myOnText == null ? "ON" : myOnText;
  }

  public void setOnText(String onText) {
    myOnText = onText;
  }

  public String getOffText() {
    return myOffText == null ? "OFF" : myOffText;
  }

  public void setOffText(String offText) {
    myOffText = offText;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
  }

  public static class OnOffButtonUI extends BasicToggleButtonUI {
    private final OnOffButton myButton;

    public OnOffButtonUI(OnOffButton checkBox) {
      myButton = checkBox;
    }

    @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
    public static ComponentUI createUI(JComponent c) {
      c.setOpaque(false);
      c.setAlignmentY(0.5f);
      return new OnOffButtonUI((OnOffButton)c);
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
      final OnOffButton button = (OnOffButton)c;
      String text = button.getOffText().length() > button.getOnText().length() ? button.getOffText() : button.getOnText();
      text = text.toUpperCase();
      final FontMetrics fm = c.getFontMetrics(c.getFont());
      int w = fm.stringWidth(text);
      int h = fm.getHeight();
      h += 2*4;
      w += 3 * h / 2;
      return new Dimension(w, h);
    }
    @Override
    public void paint(Graphics gr, JComponent c) {
      final OnOffButton button = (OnOffButton)c;
      final Dimension size = button.getSize();
      int w = size.width-8;
      int h = size.height-6;
      if (h % 2 == 1) {
        h--;
      }
      Graphics2D g = ((Graphics2D)gr);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int xOff = (myButton.getWidth() - w) / 2;
      int yOff = (myButton.getHeight() - h) / 2;
      g.translate(xOff, yOff);
      if (button.isSelected()) {
        g.setColor(new JBColor(new Color(74, 146, 73), new Color(77, 105, 76)));
        g.fillRoundRect(0, 0, w, h, 5, 5);
        g.setColor(new JBColor(Gray._192, Gray._80));
        g.drawRoundRect(0, 0, w, h, 5, 5);
        g.setColor(new JBColor(Gray._200, Gray._100));
        g.fillRoundRect(w - h, 1, h, h-1, 3, 3);
        g.setColor(UIUtil.getListForeground(true));
        g.drawString(button.getOnText(), h/2, h - 4);
      } else {
        g.setColor(UIUtil.getPanelBackground());
        g.fillRoundRect(0, 0, w, h, 5, 5);
        g.setColor(new JBColor(Gray._192, Gray._100));
        g.drawRoundRect(0, 0, w, h, 5, 5);
        g.setColor(UIUtil.getLabelDisabledForeground());
        g.drawString(button.getOffText(), h + 4 , h - 4);
        g.setColor(UIUtil.getBorderColor());
        g.setPaint(new GradientPaint(h, 0, new JBColor(Gray._158, Gray._100), 0, h, new JBColor(Gray._210, Gray._100)));
        g.fillRoundRect(0, 0, h, h, 3, 3);
//        g.setColor(UIUtil.getBorderColor());
//        g.drawOval(0, 0, h, h);
      }
      g.translate(-xOff, -yOff);
    }

    @Override
    public Dimension getMinimumSize(JComponent c) {
      return getPreferredSize(c);
    }

    @Override
    public Dimension getMaximumSize(JComponent c) {
      return getPreferredSize(c);
    }
  }
}
