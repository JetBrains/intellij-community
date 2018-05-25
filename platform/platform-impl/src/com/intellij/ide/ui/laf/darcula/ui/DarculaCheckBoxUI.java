// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.util.SystemInfo;
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
  private static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(18)).asUIResource();

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaCheckBoxUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
    if (UIUtil.getParentOfType(CellRendererPane.class, c) != null) {
      c.setBorder(null);
    }
  }

  @Override public void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setIconTextGap(textIconGap());
  }

  protected int textIconGap() {
    return JBUI.scale(5);
  }

  @Override
  public synchronized void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    Dimension size = c.getSize();

    AbstractButton b = (AbstractButton) c;
    Rectangle viewRect = updateViewRect(b, new Rectangle(size));
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = SwingUtilities2.getFontMetrics(c, g, f);

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

  protected Rectangle updateViewRect(AbstractButton b, Rectangle viewRect) {
    return viewRect;
  }

  protected void drawCheckIcon(JComponent c, Graphics2D g, AbstractButton b, Rectangle iconRect, boolean selected, boolean enabled) {
    String iconName = isIndeterminate(b) ? "checkBoxIndeterminate" : "checkBox";
    Icon icon = LafIconLookup.getIcon(iconName, selected || isIndeterminate(b), c.hasFocus(), b.isEnabled());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
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

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return updatePreferredSize(c, super.getPreferredSize(c));
  }

  protected Dimension updatePreferredSize(JComponent c, Dimension size) {
    if (c.getBorder() instanceof DarculaCheckBoxBorder) {
      JBInsets.removeFrom(size, c.getInsets());
    }
    return size;
  }

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }

  protected boolean isIndeterminate(AbstractButton checkBox) {
    return "indeterminate".equals(checkBox.getClientProperty("JButton.selectedState")) ||
      checkBox instanceof ThreeStateCheckBox && ((ThreeStateCheckBox)checkBox).getState() == ThreeStateCheckBox.State.DONT_CARE;
  }
}
