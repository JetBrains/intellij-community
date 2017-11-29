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
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.util.ui.*;
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
  static final float DISABLED_ALPHA_LEVEL = 0.47f;

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
    ((AbstractButton)c).setRolloverEnabled(true);
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
  public void paint(Graphics g, JComponent c) {
    if (isHelpButton(c)) {
      Icon help = MacIntelliJIconCache.getIcon("winHelp");
      Insets i = c.getInsets();
      help.paintIcon(c, g, i.left, i.top + (c.getHeight() - help.getIconHeight()) / 2);
    } else if (c instanceof AbstractButton) {
      AbstractButton b = (AbstractButton)c;
      ButtonModel bm = b.getModel();

      Graphics2D g2 = (Graphics2D)g.create();
      try {
        Rectangle r = new Rectangle(c.getSize());
        Container parent = c.getParent();
        if (c.isOpaque() && parent != null) {
          g2.setColor(parent.getBackground());
          g2.fill(r);
        }

        JBInsets.removeFrom(r, JBUI.insets(2));
        if (UIUtil.getParentOfType(ActionToolbar.class, c) != null) {
          JBInsets.removeFrom(r, JBUI.insetsRight(3));
        }

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);

        Color color = bm.isPressed() ? UIManager.getColor("Button.intellij.native.pressedBackgroundColor") :
                      c.hasFocus() || bm.isRollover() ? UIManager.getColor("Button.intellij.native.focusedBackgroundColor") :
                      c.getBackground();

        if (!b.isEnabled()) {
          g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, DISABLED_ALPHA_LEVEL));
        }

        g2.setColor(color);
        g2.fill(r);

        paintContents(g2, b);
      } finally {
        g2.dispose();
      }
    }
  }

  @Override protected void modifyViewRect(AbstractButton b, Rectangle rect) {
    if (!isComboButton(b)) {
      JBInsets.removeFrom(rect, b.getInsets());
    }

    if (isComboButton(b)) {
      rect.x += JBUI.scale(5);
    } else if (b instanceof JBOptionButton) {
      rect.x -= JBUI.scale(4);
    }

    rect.y -= JBUI.scale(1); // Move one pixel up
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    if (isHelpButton(c)) {
      Icon icon = MacIntelliJIconCache.getIcon("winHelp");
      Insets i = c.getInsets();
      return new Dimension(icon.getIconWidth() + i.left + i.right, JBUI.scale(24));
    } else if (isSquare(c)) {
      return new JBDimension(24, 24);
    } else {
      return super.getPreferredSize(c);
    }
  }

  @Override
  protected void setupDefaultButton(JButton button) {
    //do nothing
  }

  @Override
  protected void paintDisabledText(Graphics g, String text, JComponent c, Rectangle textRect, FontMetrics metrics) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setColor(UIManager.getColor("Button.disabledText"));
      SwingUtilities2.drawStringUnderlineCharAt(c, g2, text, -1,
                                                textRect.x + getTextShiftOffset(),
                                                textRect.y + metrics.getAscent() + getTextShiftOffset());
    } finally {
      g2.dispose();
    }
  }
}
