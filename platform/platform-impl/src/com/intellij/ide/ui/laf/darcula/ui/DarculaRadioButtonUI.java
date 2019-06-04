// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.util.ui.*;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import javax.swing.text.View;
import java.awt.*;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isMultiLineHTML;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRadioButtonUI extends MetalRadioButtonUI {
  private static final Icon DEFAULT_ICON = JBUI.scale(EmptyIcon.create(19)).asUIResource();

  private final PropertyChangeListener textChangedListener = e -> updateTextPosition((AbstractButton)e.getSource());

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaRadioButtonUI();
  }

  @Override
  public void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setIconTextGap(textIconGap());
    updateTextPosition(b);
  }

  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    b.addPropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, textChangedListener);
  }

  @Override
  protected void uninstallListeners(AbstractButton button) {
    super.uninstallListeners(button);
    button.removePropertyChangeListener(AbstractButton.TEXT_CHANGED_PROPERTY, textChangedListener);
  }

  protected int textIconGap() {
    return JBUIScale.scale(4);
  }

  private static void updateTextPosition(AbstractButton b) {
    b.setVerticalTextPosition(isMultiLineHTML(b.getText()) ? SwingConstants.TOP : SwingConstants.CENTER);
  }

  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    Dimension size = c.getSize();

    AbstractButton b = (AbstractButton)c;
    Rectangle viewRect = updateViewRect(b, new Rectangle(size));
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();

    Font f = c.getFont();
    g.setFont(f);
    FontMetrics fm = UIUtilities.getFontMetrics(c, g, f);

    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), getDefaultIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, b.getIconTextGap());

    if (c.isOpaque()) {
      g.setColor(c.getBackground());
      g.fillRect(0, 0, size.width, size.height);
    }

    paintIcon(c, g, viewRect, iconRect);
    drawText(b, g, text, textRect, fm);
  }

  protected Rectangle updateViewRect(AbstractButton b, Rectangle viewRect) {
    if (!(b.getBorder() instanceof DarculaRadioButtonBorder)) {
      JBInsets.removeFrom(viewRect, b.getInsets());
    }
    return viewRect;
  }

  protected void paintIcon(JComponent c, Graphics2D g, Rectangle viewRect, Rectangle iconRect) {
    Icon icon = LafIconLookup.getIcon("radio", ((AbstractButton)c).isSelected(), c.hasFocus(), c.isEnabled());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  protected void drawText(AbstractButton b, Graphics2D g, String text, Rectangle textRect, FontMetrics fm) {
    if (text != null) {
      View v = (View)b.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      }
      else {
        g.setColor(b.isEnabled() ? b.getForeground() : getDisabledTextColor());
        int mnemonicIndex = DarculaLaf.isAltPressed() ? b.getDisplayedMnemonicIndex() : -1;
        UIUtilities.drawStringUnderlineCharAt(b, g, text, mnemonicIndex, textRect.x, textRect.y + fm.getAscent());
      }
    }

    if (b.hasFocus() && b.isFocusPainted() &&
        textRect.width > 0 && textRect.height > 0) {
      paintFocus(g, textRect, b.getSize());
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return updatePreferredSize(c, super.getPreferredSize(c));
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return getPreferredSize(c);
  }

  protected Dimension updatePreferredSize(JComponent c, Dimension size) {
    if (c.getBorder() instanceof DarculaRadioButtonBorder) {
      JBInsets.removeFrom(size, c.getInsets());
    }
    return size;
  }

  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {}

  @Override
  public Icon getDefaultIcon() {
    return DEFAULT_ICON;
  }
}
