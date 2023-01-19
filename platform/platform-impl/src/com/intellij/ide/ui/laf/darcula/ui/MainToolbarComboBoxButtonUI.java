// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.hover.HoverListener;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

public class MainToolbarComboBoxButtonUI extends DarculaButtonUI {

  public static ComponentUI createUI(JComponent c) {
    return new MainToolbarComboBoxButtonUI();
  }

  //private static final int ELEMENTS_GAP = 5;
  private static final Icon EXPAND_ICON = AllIcons.General.ChevronDown;

  private static final @NotNull JBColor HOVER_COLOR = JBColor.namedColor("MainToolbar.Dropdown.hoverBackground", JBColor.background());
  private static final @NotNull JBColor COLOR = JBColor.namedColor("MainToolbar.Dropdown.background", JBColor.foreground());
  private static final Object HOVER_PROP = "MainToolbarComboBoxButtonUI.isHovered";

  private final HoverListener listener = new HoverListener() {

    @Override
    public void mouseEntered(@NotNull Component component, int x, int y) {
      component.setBackground(HOVER_COLOR);
      ((JComponent) component).putClientProperty(HOVER_PROP, Boolean.TRUE);
      component.repaint();
    }

    @Override
    public void mouseExited(@NotNull Component component) {
      component.setBackground(COLOR);
      ((JComponent) component).putClientProperty(HOVER_PROP, null);
      component.repaint();
    }

    @Override
    public void mouseMoved(@NotNull Component component, int x, int y) {}
  };

  @Override
  public void installUI(JComponent c) {
    c.setForeground(JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground()));
    c.setBackground(COLOR);

    Insets insets = UIManager.getInsets("MainToolbar.Dropdown.borderInsets");
    JBEmptyBorder border = JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right);
    c.setBorder(border);
    c.setOpaque(true);

    listener.addTo(c);
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);
    listener.removeFrom(c);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    if (!(c instanceof ComboBoxAction.ComboBoxButton)) return;
    ComboBoxAction.ComboBoxButton button = (ComboBoxAction.ComboBoxButton)c;

    if (button.getClientProperty(HOVER_PROP) == Boolean.TRUE) paintBackground(g, button, HOVER_COLOR);
    else if (c.isOpaque()) paintBackground(g, button, button.getBackground());
    paintContents(g, button);
  }

  @Override
  protected void paintContents(Graphics g, AbstractButton c) {
    if (!(c instanceof ComboBoxAction.ComboBoxButton)) return;
    ComboBoxAction.ComboBoxButton button = (ComboBoxAction.ComboBoxButton)c;

    FontMetrics fm = UIUtilities.getFontMetrics(button, g);
    int width = button.getWidth();
    int height = button.getHeight();
    if (button.isArrowVisible()) {
      width -= button.getIconTextGap() + EXPAND_ICON.getIconWidth();
    }
    String text = layout(button, button.getText(), button.getIcon(), fm, width, height);

    Graphics g2 = g.create();
    try {
      g2.setColor(button.getForeground());
      if (button.getIcon() != null) {
        paintIcon(g, button, iconRect);
      }

      if (text != null && !text.isEmpty()) {
        View v = (View)button.getClientProperty(BasicHTML.propertyKey);
        if (v != null) {
          v.paint(g, textRect);
        }
        else {
          UISettings.setupAntialiasing(g);
          paintText(g, button, textRect, text);
        }
      }

      if (button.isArrowVisible()) {
        int x = ((int)textRect.getMaxX()) + button.getIconTextGap();
        int y = height / 2 - EXPAND_ICON.getIconHeight() / 2;
        EXPAND_ICON.paintIcon(button, g2, x, y);
      }
    }
    finally {
      g2.dispose();
    }
  }

  //@Override
  //public Dimension getMinimumSize(JComponent c) {
  //  Dimension size = super.getMinimumSize(c);
  //  if (!(c instanceof ComboBoxAction.ComboBoxButton)) return size;
  //
  //  AbstractButton button = (AbstractButton)c;
  //
  //  if (((ComboBoxAction.ComboBoxButton)c).isArrowVisible()) size.width += button.getIconTextGap() + EXPAND_ICON.getIconWidth();
  //  if (StringUtil.isNotEmpty(button.getText())) size.width += button.getIconTextGap();
  //
  //  JBInsets.addTo(size, button.getMargin());
  //  return size;
  //}

  @Override
  protected Dimension getDarculaButtonSize(JComponent c, Dimension prefSize) {
    Dimension size = super.getDarculaButtonSize(c, prefSize);
    if (!(c instanceof ComboBoxAction.ComboBoxButton)) return size;
    AbstractButton button = (AbstractButton)c;

    if (((ComboBoxAction.ComboBoxButton)c).isArrowVisible())
      size.width += ((AbstractButton)c).getIconTextGap() + EXPAND_ICON.getIconWidth();
    if (StringUtil.isNotEmpty(button.getText())) size.width += button.getIconTextGap();

    JBInsets.addTo(size, button.getMargin());
    return size;
  }

  private static void paintBackground(Graphics g, JComponent c, Color color) {
    Graphics g2 = g.create();
    try {
      g2.setColor(color);
      Rectangle bounds = g2.getClipBounds();
      g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    } finally {
      g2.dispose();
    }
  }
}
