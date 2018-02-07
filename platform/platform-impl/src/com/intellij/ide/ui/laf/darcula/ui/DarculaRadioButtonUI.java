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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.intellij.MacIntelliJIconCache;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import javax.swing.text.View;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRadioButtonUI extends MetalRadioButtonUI {
  private static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(19)).asUIResource();

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaRadioButtonUI();
  }

  @Override public void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setIconTextGap(JBUI.scale(4));
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

    // fill background
    if(c.isOpaque()) {
      g.setColor(c.getBackground());
      g.fillRect(0,0, size.width, size.height);
    }

    paintIcon(c, g, viewRect, iconRect);
    drawText(b, g, text, textRect, fm);
  }

  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    Icon icon = MacIntelliJIconCache.getIcon("radio", ((AbstractButton)c).isSelected(), c.hasFocus(), c.isEnabled());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  protected void drawText(AbstractButton b, Graphics2D g, String text, Rectangle textRect, FontMetrics fm) {
    if(text != null) {
      View v = (View) b.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      } else {
        int mnemonicIndex = b.getDisplayedMnemonicIndex();
        g.setColor(b.isEnabled() ? b.getForeground() : getDisabledTextColor());
        SwingUtilities2.drawStringUnderlineCharAt(b, g, text, mnemonicIndex, textRect.x, textRect.y + fm.getAscent());
      }
    }

    if(b.hasFocus() && b.isFocusPainted() &&
       textRect.width > 0 && textRect.height > 0 ) {
        paintFocus(g, textRect, b.getSize());
    }
  }

  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {}

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }
}
