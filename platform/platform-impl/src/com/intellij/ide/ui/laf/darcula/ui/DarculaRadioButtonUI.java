// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.LookAndFeelThemeAdapter;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.LafIconLookup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalRadioButtonUI;
import java.awt.*;
import java.beans.PropertyChangeListener;

import static com.intellij.ide.ui.laf.darcula.DarculaUIUtil.isMultiLineHTML;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaRadioButtonUI extends MetalRadioButtonUI {

  private static Icon defaultIconCache;

  private final PropertyChangeListener textChangedListener = e -> updateTextPosition((AbstractButton)e.getSource());

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaRadioButtonUI();
  }

  @Override
  public void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setIconTextGap(textIconGap(b));
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

  protected int textIconGap(AbstractButton b) {
    Object gap = UIManager.get("RadioButton.iconTextGap");
    if (gap != null) {
      try {
        return JBUIScale.scale(Integer.parseInt(gap.toString()));
      }
      catch (NumberFormatException ignored) {
      }
    }
    return JBUIScale.scale(4);
  }

  private static void updateTextPosition(AbstractButton b) {
    b.setVerticalTextPosition(isMultiLineHTML(b.getText()) ? SwingConstants.TOP : SwingConstants.CENTER);
  }

  @SuppressWarnings("NonSynchronizedMethodOverridesSynchronizedMethod")
  @Override
  public void paint(Graphics g2d, JComponent c) {
    Graphics2D g = (Graphics2D)g2d;
    AbstractButton button = (AbstractButton)c;
    AbstractButtonLayout layout = createLayout(button, button.getSize());

    layout.paint(g, getDisabledTextColor(), getMnemonicIndex(button));
    paintFocus(button, g, layout.textRect);
    paintIcon(c, g, layout.iconRect);
  }

  protected boolean removeInsetsBeforeLayout(AbstractButton b) {
    return !(b.getBorder() instanceof DarculaRadioButtonBorder);
  }

  protected void paintIcon(JComponent c, Graphics2D g, Rectangle iconRect) {
    Icon icon = LafIconLookup.getIcon("radio", ((AbstractButton)c).isSelected(), c.hasFocus(), c.isEnabled());
    icon.paintIcon(c, g, iconRect.x, iconRect.y);
  }

  private void paintFocus(AbstractButton b, Graphics2D g, Rectangle textRect) {
    if (b.hasFocus() && b.isFocusPainted() &&
        textRect.width > 0 && textRect.height > 0) {
      paintFocus(g, textRect, b.getSize());
    }
  }

  @Override
  public int getBaseline(JComponent c, int width, int height) {
    AbstractButtonLayout layout = createLayout(c, new Dimension(width, height));
    return layout.getBaseline();
  }

  protected int getMnemonicIndex(AbstractButton b) {
    return LookAndFeelThemeAdapter.isAltPressed() ? b.getDisplayedMnemonicIndex() : -1;
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    AbstractButtonLayout layout = createLayout(c, new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
    return layout.getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return getPreferredSize(c);
  }

  @Override
  protected void paintFocus(Graphics g, Rectangle t, Dimension d) {}

  @Override
  public Icon getDefaultIcon() {
    int iconSize = JBUI.getInt("RadioButton.iconSize", 19);
    if (defaultIconCache == null || defaultIconCache.getIconWidth() != iconSize || defaultIconCache.getIconHeight() != iconSize) {
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      defaultIconCache = JBUIScale.scaleIcon(EmptyIcon.create(iconSize)).asUIResource();
    }
    return defaultIconCache;
  }

  private @NotNull AbstractButtonLayout createLayout(JComponent c, Dimension size) {
    AbstractButton button = (AbstractButton)c;
    return new AbstractButtonLayout(button, size, removeInsetsBeforeLayout(button), getDefaultIcon());
  }
}
