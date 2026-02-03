// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;
import java.util.Locale;

/**
 * @author Konstantin Bulenkov
 */
public class OnOffButton extends JToggleButton {
  private @NlsContexts.Button String myOnText = IdeBundle.message("ui.button.on");
  private @NlsContexts.Button String myOffText = IdeBundle.message("ui.button.off");

  /**
   * Internal padding
   */
  private @NotNull Insets myIpad = JBUI.insets(3);

  public OnOffButton() {
    setBorder(null);
    setOpaque(false);
  }

  public @NlsContexts.Button String getOnText() {
    return myOnText;
  }

  @SuppressWarnings("unused")
  public void setOnText(@NlsContexts.Button String onText) {
    myOnText = onText;
  }

  public @NlsContexts.Button String getOffText() {
    return myOffText;
  }

  @SuppressWarnings("unused")
  public void setOffText(@NlsContexts.Button String offText) {
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

  /**
   * Sets specified internal paddings
   *
   * @param ipad insets
   */
  public void setIpad(@NotNull Insets ipad) {
    myIpad = ipad;
  }

  /**
   * @return internal paddings of the component
   */
  public @NotNull Insets getIpad() {
    return myIpad;
  }

  private static class DefaultOnOffButtonUI extends BasicToggleButtonUI {
    private static final Color BORDER_COLOR = JBColor.namedColor("ToggleButton.borderColor", new JBColor(Gray._192, Gray._80));
    private static final Color BUTTON_COLOR = JBColor.namedColor("ToggleButton.buttonColor", new JBColor(Gray._200, Gray._100));
    private static final Color ON_BACKGROUND = JBColor.namedColor("ToggleButton.onBackground", new JBColor(new Color(74, 146, 73), new Color(77, 105, 76)));
    private static final Color ON_FOREGROUND = JBColor.namedColor("ToggleButton.onForeground", JBColor.lazy(() -> UIUtil.getListForeground(true, true)));

    private static final Color OFF_BACKGROUND = JBColor.namedColor("ToggleButton.offBackground", JBColor.lazy(() -> UIUtil.getPanelBackground()));
    private static final Color OFF_FOREGROUND = JBColor.namedColor("ToggleButton.offForeground", JBColor.lazy(() -> UIUtil.getLabelDisabledForeground()));

    @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
    public static ComponentUI createUI(JComponent c) {
      c.setAlignmentY(0.5f);
      return new DefaultOnOffButtonUI();
    }

    @Override
    public Dimension getPreferredSize(JComponent c) {
      OnOffButton button = (OnOffButton)c;
      Insets ipad = button.getIpad();
      int textWidth = Math.max(getTextWidth(button, button.getOnText()), getTextWidth(button, button.getOffText()));
      int fmHeight = c.getFontMetrics(c.getFont()).getHeight();
      int widthCorrection = (int)(JBUIScale.scale(3) * 0.5); // A magic constant from the past
      int w = textWidth + (int)(fmHeight * 1.25) + widthCorrection + ipad.left + ipad.right;
      int h = fmHeight + ipad.top + ipad.bottom;
      return new Dimension(w, h);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
      if (!(c instanceof OnOffButton button)) return;

      int toggleArc = JBUIScale.scale(3);
      int buttonArc = JBUIScale.scale(5);
      Dimension size = button.getSize();
      Insets ipad = button.getIpad();
      int w = size.width - (ipad.left + ipad.right);
      int h = size.height - (ipad.top + ipad.bottom);
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

        int knobWidth = w - SwingUtilities.computeStringWidth(g2.getFontMetrics(), button.getOffText()) - JBUIScale.scale(2);
        knobWidth = Math.min(knobWidth, h);

        int textAscent = g2.getFontMetrics().getAscent();

        Rectangle viewRect = new Rectangle();
        Rectangle textRect = new Rectangle();
        Rectangle iconRect = new Rectangle();

        g2.setColor(BUTTON_COLOR);
        if (selected) {
          g2.fillRoundRect(w - knobWidth, 0, knobWidth, h, toggleArc, toggleArc);

          if (button.getOnText() != null) {
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
        }
        else {
          g2.fillRoundRect(0, 0, knobWidth, h, toggleArc, toggleArc);

          if (button.getOffText() != null) {
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

        g2.setColor(BORDER_COLOR);
        g2.drawRoundRect(0, 0, w, h, buttonArc, buttonArc);
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

    private static int getTextWidth(OnOffButton button, @Nullable String text) {
      if (text == null) {
        return 0;
      }

      FontMetrics fm = button.getFontMetrics(button.getFont());
      return fm.stringWidth(text.toUpperCase(Locale.getDefault()));
    }
  }
}
