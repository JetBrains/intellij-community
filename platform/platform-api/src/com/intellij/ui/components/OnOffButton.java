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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
public class OnOffButton extends JToggleButton {
  private String myOnText = "ON";
  private String myOffText = "OFF";

  public OnOffButton() {
    setBorder(null);
    setOpaque(false);
  }

  public String getOnText() {
    return myOnText;
  }

  @SuppressWarnings("unused")
  public void setOnText(String onText) {
    myOnText = onText;
  }

  public String getOffText() {
    return myOffText;
  }

  @SuppressWarnings("unused")
  public void setOffText(String offText) {
    myOffText = offText;
  }

  @Override public String getUIClassID() {
    return "OnOffButtonUI";
  }

  @Override public void updateUI() {
    // Check that class name is in the UI table before creating UI delegate from it.
    // If the custom class name is not listed (like for example in system LaFs) then
    // use the default delegate.
    Object uiClassName = UIManager.get(getUIClassID());
    setUI(uiClassName == null ?
          DefaultOnOffButtonUI.createUI(this) :
          UIManager.getUI(this));
  }

  private static class DefaultOnOffButtonUI extends BasicToggleButtonUI {
    @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
    public static ComponentUI createUI(JComponent c) {
      c.setAlignmentY(0.5f);
      return new DefaultOnOffButtonUI();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
      int vGap = JBUI.scale(4);

      OnOffButton button = (OnOffButton)c;
      String text = button.getOffText().length() > button.getOnText().length() ? button.getOffText() : button.getOnText();
      text = text.toUpperCase(Locale.getDefault());
      FontMetrics fm = c.getFontMetrics(c.getFont());
      int w = fm.stringWidth(text);
      int h = fm.getHeight();
      h += 2 * vGap;
      w += 3 * h / 2;
      return new Dimension(w, h);
    }

    @Override
    public void paint(Graphics gr, JComponent c) {
      if (!(c instanceof OnOffButton)) return;

      int toggleArc = JBUI.scale(3);
      int buttonArc = JBUI.scale(5);
      int vGap = JBUI.scale(4);
      int hGap = JBUI.scale(3);
      int border = 1;

      OnOffButton button = (OnOffButton)c;
      Dimension size = button.getSize();
      int w = size.width - 2 * vGap;
      int h = size.height - 2 * hGap;
      if (h % 2 == 1) {
        h--;
      }
      Graphics2D g = ((Graphics2D)gr);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      int xOff = (button.getWidth() - w) / 2;
      int yOff = (button.getHeight() - h) / 2;
      g.translate(xOff, yOff);
      if (button.isSelected()) {
        g.setColor(new JBColor(new Color(74, 146, 73), new Color(77, 105, 76)));
        g.fillRoundRect(0, 0, w, h, buttonArc, buttonArc);
        g.setColor(new JBColor(Gray._192, Gray._80));
        g.drawRoundRect(0, 0, w, h, buttonArc, buttonArc);
        g.setColor(new JBColor(Gray._200, Gray._100));
        g.fillRoundRect(w - h, border, h, h - border, toggleArc, toggleArc);
        g.setColor(UIUtil.getListForeground(true));
        g.drawString(button.getOnText(), h / 2, h - vGap);
      }
      else {
        g.setColor(UIUtil.getPanelBackground());
        g.fillRoundRect(0, 0, w, h, buttonArc, buttonArc);
        g.setColor(new JBColor(Gray._192, Gray._100));
        g.drawRoundRect(0, 0, w, h, buttonArc, buttonArc);
        g.setColor(UIUtil.getLabelDisabledForeground());
        g.drawString(button.getOffText(), h + vGap, h - vGap);
        g.setColor(JBColor.border());
        g.setPaint(new GradientPaint(h, 0, new JBColor(Gray._158, Gray._100), 0, h, new JBColor(Gray._210, Gray._100)));
        g.fillRoundRect(0, 0, h, h, toggleArc, toggleArc);
        // g.setColor(UIUtil.getBorderColor());
        // g.drawOval(0, 0, h, h);
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
