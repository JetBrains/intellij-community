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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * @author Konstantin Bulenkov
 */
public class WinIntelliJButtonUI extends DarculaButtonUI {
  private PropertyChangeListener helpButtonListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      final Object source = evt.getSource();
      if (source instanceof AbstractButton) {
        if (isHelpButton((JComponent)source)) {
          ((AbstractButton)source).setOpaque(false);
        }
      }
    }
  };

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new WinIntelliJButtonUI();
  }

  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    b.addPropertyChangeListener("JButton.buttonType", helpButtonListener);
  }

  @Override
  protected void uninstallListeners(AbstractButton b) {
    b.removePropertyChangeListener("JButton.buttonType", helpButtonListener);
    super.uninstallListeners(b);
  }

  @Override
  protected boolean paintDecorations(Graphics2D g, JComponent c) {
    if (isHelpButton(c)) {
      final Icon help = MacIntelliJIconCache.getIcon("winHelp");
      help.paintIcon(c, g, (c.getWidth() - help.getIconWidth()) / 2, (c.getHeight() - help.getIconHeight()) / 2);
      return false;
    }
    final Insets i = c.getInsets();
    g.setColor(c.hasFocus() ? UIManager.getColor("Button.intellij.native.activeBackgroundColor") : c.getBackground());
    if (c.getBorder() instanceof WinIntelliJButtonPainter) {
     g.fillRect(0,0,c.getWidth(), c.getHeight());
    } else {
      g.fillRect(i.left, i.top, c.getWidth() - i.left - i.right, c.getHeight() - i.top - i.bottom);
    }
    return true;
  }

  @Override
  protected void setupDefaultButton(JButton button) {
    //do nothing
  }

  @Override
  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    g.setColor(UIManager.getColor("Button.disabledText"));
    SwingUtilities2.drawStringUnderlineCharAt(c, g, text, -1,
                                              textRect.x + getTextShiftOffset(),
                                              textRect.y + metrics.getAscent() + getTextShiftOffset());
  }
}
