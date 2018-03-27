/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.util.ui.*;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalCheckBoxUI;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaCheckBoxUI extends MetalCheckBoxUI {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    if (UIUtil.getParentOfType(CellRendererPane.class, c) != null) {
      c.setBorder(null);
    }
    return new DarculaCheckBoxUI();
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    Dimension size = c.getSize();

    Rectangle viewRect = new Rectangle(size);
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    AbstractButton b = (AbstractButton) c;

    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);

    JBInsets.removeFrom(viewRect, c.getInsets());

    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), getDefaultIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, b.getIconTextGap());

    //background
    if (c.isOpaque()) {
      g.setColor(b.getBackground());
      g.fillRect(0, 0, size.width, size.height);
    }

    drawCheckIcon(c, g, b, iconRect, b.isSelected(), b.isEnabled());
    drawText(c, g, b, fm, textRect, text);
  }

  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    if (selected && b.getSelectedIcon() != null) {
      b.getSelectedIcon().paintIcon(b, g, iconRect.x + JBUI.scale(4), iconRect.y + JBUI.scale(2));
    } else if (!selected && b.getIcon() != null) {
      b.getIcon().paintIcon(b, g, iconRect.x + JBUI.scale(4), iconRect.y + JBUI.scale(2));
    } else {
      g = (Graphics2D)g.create();
      try {
        int off = JBUI.scale(3);
        int x = iconRect.x + off;
        int y = iconRect.y + off;
        int w = iconRect.width - 2 * off;
        int h = iconRect.height - 2 * off;

        g.translate(x, y);

        //setup AA for lines
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);

        if (c.isEnabled() || !UIUtil.isUnderDarcula()) {
          Paint paint = UIUtil.getGradientPaint(w / 2, 0, b.getBackground().brighter(),
                                                w / 2, h, b.getBackground());
          g.setPaint(paint);

          int fillOffset = JBUI.scale(1);
          g.fillRect(fillOffset, fillOffset, w - 2 * fillOffset, h - 2 * fillOffset);
        }

        boolean armed = b.getModel().isArmed();

        int R = JBUI.scale(4);
        int offset = 1;
        boolean overrideBg = isIndeterminate(b) && fillBackgroundForIndeterminateSameAsForSelected();

        if (c.hasFocus()) {
          g.setPaint(UIUtil.getGradientPaint(w/2, offset, getFocusedBackgroundColor1(armed, selected || overrideBg),
                                             w/2, h, getFocusedBackgroundColor2(armed, selected || overrideBg)));
          g.fillRoundRect(0, 0, w, h, R, R);

          float bw = DarculaUIUtil.bw();
          g.translate(-bw, -bw);
          DarculaUIUtil.paintFocusBorder(g, (int)(iconRect.width - bw), (int)(iconRect.height - bw), DarculaUIUtil.arc(), true);
          g.translate(bw, bw);
        } else {
          if (c.isEnabled() || !UIUtil.isUnderDarcula()) {
            g.setPaint(UIUtil.getGradientPaint(w / 2, offset, getBackgroundColor1(enabled, selected || overrideBg),
                                               w / 2, h, getBackgroundColor2(enabled, selected || overrideBg)));
            g.fillRoundRect(0, 0, w, h , R, R);

            Color borderColor1 = getBorderColor1(enabled, selected || overrideBg);
            Color borderColor2 = getBorderColor2(enabled, selected || overrideBg);
            g.setPaint(UIUtil.getGradientPaint(w / 2, offset, borderColor1, w / 2, h, borderColor2));
            g.drawRoundRect(0, (UIUtil.isUnderDarcula() ? offset : 0), w, h - offset, R, R);

            g.setPaint(getInactiveFillColor());
            g.drawRoundRect(0, 0, w, h - offset, R, R);
          } else {
            g.setColor(Gray.x58);
            g.drawRoundRect(0, 0, w, h - offset, R, R);
          }
        }

        if (isIndeterminate(b)) {
          paintIndeterminateSign(g, enabled, w, h);
        } else if (b.getModel().isSelected()) {
          paintCheckSign(g, enabled, w, h);
        }
      } finally {
        g.dispose();
      }
    }
  }

  protected void paintIndeterminateSign(Graphics2D g, boolean enabled, int w, int h) {
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setStroke(new BasicStroke(1 * JBUI.scale(2.0f), BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));

    int off = JBUI.scale(4);
    int y1 = h / 2;
    g.setColor(getShadowColor(enabled, true));
    GraphicsConfig c = new GraphicsConfig(g).paintWithAlpha(.8f);
    g.drawLine(off, y1 + JBUI.scale(1), w - off + JBUI.scale(1), y1 + JBUI.scale(1));
    c.restore();
    g.setColor(getCheckSignColor(enabled, true));
    g.drawLine(off, y1, w - off + JBUI.scale(1), y1);
  }

  protected void drawText(JComponent c, Graphics2D g, AbstractButton b, FontMetrics fm, Rectangle textRect, String text) {
    //text
    if(text != null) {
      View view = (View) c.getClientProperty(BasicHTML.propertyKey);
      if (view != null) {
        view.paint(g, textRect);
      } else {
        g.setColor(b.isEnabled() ? b.getForeground() : getDisabledTextColor());
        final int mnemonicIndex = SystemInfo.isMac && !UIManager.getBoolean("Button.showMnemonics") ? -1 : b.getDisplayedMnemonicIndex();
        SwingUtilities2.drawStringUnderlineCharAt(c, g, text,
                                                  mnemonicIndex,
                                                  textRect.x,
                                                  textRect.y + fm.getAscent());
      }
    }
  }

  protected boolean fillBackgroundForIndeterminateSameAsForSelected() {
    return false;
  }

  protected void paintCheckSign(Graphics2D g, boolean enabled, int w, int h) {
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setStroke(new BasicStroke(1 * JBUI.scale(2.0f), BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));

    int x1 = JBUI.scale(4);
    int y1 = JBUI.scale(7);
    int x2 = JBUI.scale(7);
    int y2 = JBUI.scale(11);
    int y3 = JBUI.scale(2);

    if (enabled) {
      g.setPaint(getShadowColor(true, true));
      g.drawLine(x1, y1, x2, y2);
      g.drawLine(x2, y2, w, y3);
    }

    g.setPaint(getCheckSignColor(enabled, true));
    g.translate(0, -2);
    g.drawLine(x1, y1, x2, y2);
    g.drawLine(x2, y2, w, y3);
    g.translate(0, 2);
  }

  protected Color getInactiveFillColor() {
    return getColor("inactiveFillColor", Gray._40.withAlpha(180));
  }

  protected Color getBorderColor1(boolean enabled, boolean selected) {
    return enabled ? getColor("borderColor1", Gray._120.withAlpha(0x5a), selected)
                   : getColor("disabledBorderColor1", Gray._120.withAlpha(90), selected);
  }

  protected Color getBorderColor2(boolean enabled, boolean selected) {
    return enabled ? getColor("borderColor2", Gray._105.withAlpha(90), selected)
                   : getColor("disabledBorderColor2", Gray._105.withAlpha(90), selected);
  }

  protected Color getBackgroundColor1(boolean enabled, boolean selected) {
    return getColor("backgroundColor1", Gray._110, selected);
  }

  protected Color getBackgroundColor2(boolean enabled, boolean selected) {
    return getColor("backgroundColor2", Gray._95, selected);
  }

  protected Color getCheckSignColor(boolean enabled, boolean selected) {
    return enabled ? getColor("checkSignColor", Gray._170, selected)
                   : getColor("checkSignColorDisabled", Gray._120, selected);
  }

  protected Color getShadowColor(boolean enabled, boolean selected) {
    return enabled ? getColor("shadowColor", Gray._30, selected)
                   : getColor("shadowColorDisabled", Gray._60, selected);
  }

  protected Color getFocusedBackgroundColor1(boolean armed, boolean selected) {
    return armed ? getColor("focusedArmed.backgroundColor1", Gray._100, selected)
                 : getColor("focused.backgroundColor1", Gray._120, selected);
  }

  protected Color getFocusedBackgroundColor2(boolean armed, boolean selected) {
    return armed ? getColor("focusedArmed.backgroundColor2", Gray._55, selected)
                 : getColor("focused.backgroundColor2", Gray._75, selected);
  }

  protected static Color getColor(String shortPropertyName, Color defaultValue) {
    final Color color = UIManager.getColor("CheckBox.darcula." + shortPropertyName);
    return color == null ? defaultValue : color;
  }

  protected static Color getColor(String shortPropertyName, Color defaultValue, boolean selected) {
    if (selected) {
      final Color color = getColor(shortPropertyName + ".selected", null);
      if (color != null) {
        return color;
      }
    }
    return getColor(shortPropertyName, defaultValue);
  }

  @Override
  public Icon getDefaultIcon() {
    return EmptyIcon.create(JBUI.scale(20)).asUIResource();
  }

  protected boolean isIndeterminate(AbstractButton checkBox) {
    return "indeterminate".equals(checkBox.getClientProperty("JButton.selectedState")) ||
      checkBox instanceof ThreeStateCheckBox && ((ThreeStateCheckBox)checkBox).getState() == ThreeStateCheckBox.State.DONT_CARE;
  }
}
