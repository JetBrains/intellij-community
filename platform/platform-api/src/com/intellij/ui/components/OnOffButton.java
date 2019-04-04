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
    private static final Color BORDER_COLOR = JBColor.namedColor("ToggleButton.borderColor", new JBColor(Gray._192, Gray._80));
    private static final Color BUTTON_COLOR = JBColor.namedColor("ToggleButton.buttonColor", new JBColor(Gray._200, Gray._100));
    private static final Color ON_BACKGROUND = JBColor.namedColor("ToggleButton.onBackground", new JBColor(new Color(74, 146, 73), new Color(77, 105, 76)));
    private static final Color ON_FOREGROUND = JBColor.namedColor("ToggleButton.onForeground", new JBColor(() -> UIUtil.getListForeground(true, true)));

    private static final Color OFF_BACKGROUND = JBColor.namedColor("ToggleButton.offBackground", new JBColor(() -> UIUtil.getPanelBackground()));
    private static final Color OFF_FOREGROUND = JBColor.namedColor("ToggleButton.offForeground", new JBColor(() -> UIUtil.getLabelDisabledForeground()));

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
    public void paint(Graphics g, JComponent c) {
      if (!(c instanceof OnOffButton)) return;

      int toggleArc = JBUI.scale(3);
      int buttonArc = JBUI.scale(5);
      int vGap = JBUI.scale(4);
      int hGap = JBUI.scale(3);

      OnOffButton button = (OnOffButton)c;
      Dimension size = button.getSize();
      int w = size.width - 2 * vGap;
      int h = size.height - 2 * hGap;
      if (h % 2 == 1) {
        h--;
      }

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int xOff = (button.getWidth() - w) / 2;
        int yOff = (button.getHeight() - h) / 2;
        g2.translate(xOff, yOff);

        boolean selected = button.isSelected();
        g2.setColor(selected ? ON_BACKGROUND : OFF_BACKGROUND);
        g2.fillRoundRect(0, 0, w, h, buttonArc, buttonArc);

        g2.setColor(BORDER_COLOR);
        g2.drawRoundRect(0, 0, w, h, buttonArc, buttonArc);

        int knobWidth = w - SwingUtilities.computeStringWidth(g2.getFontMetrics(), button.getOffText()) - JBUI.scale(2);
        knobWidth = knobWidth > h ? h : knobWidth;

        int textAscent = g2.getFontMetrics().getAscent();

        Rectangle viewRect = new Rectangle();
        Rectangle textRect = new Rectangle();
        Rectangle iconRect = new Rectangle();

        g2.setColor(BUTTON_COLOR);
        if (selected) {
          g2.fillRoundRect(w - knobWidth, 0, knobWidth, h, toggleArc, toggleArc);

          viewRect.setBounds(0, 0, w - knobWidth, h);
          SwingUtilities.layoutCompoundLabel(g2.getFontMetrics(),
                                             button.getOnText(),
                                             null,
                                             SwingConstants.CENTER, SwingConstants.CENTER,
                                             SwingConstants.CENTER, SwingConstants.CENTER,
                                             viewRect, iconRect, textRect, 0);

          g2.setColor(ON_FOREGROUND);
          g2.drawString(button.getOnText(), textRect.x, textRect.y + textAscent);
        }
        else {
          g2.fillRoundRect(0, 0, knobWidth, h, toggleArc, toggleArc);

          viewRect.setBounds(knobWidth, 0, w - knobWidth, h);
          SwingUtilities.layoutCompoundLabel(g2.getFontMetrics(),
                                             button.getOffText(),
                                             null,
                                             SwingConstants.CENTER, SwingConstants.CENTER,
                                             SwingConstants.CENTER, SwingConstants.CENTER,
                                             viewRect, iconRect, textRect, 0);

          g2.setColor(OFF_FOREGROUND);
          g2.drawString(button.getOffText(), textRect.x, textRect.y + textAscent);
        }
      }
      finally {
        g2.dispose();
      }
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
