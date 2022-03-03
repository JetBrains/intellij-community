// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ToolbarComboWidget;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ToolbarComboWidgetUI extends ComponentUI {

  private static final int ELEMENTS_GAP = 5;
  private static final int ICONS_GAP = 5;
  private static final Icon EXPAND_ICON = AllIcons.General.ChevronDown;
  private static final int MIN_TEXT_LENGTH = 5;
  private static final int SEPARATOR_WIDTH = 1;

  private final HoverAreaTracker hoverTracker = new HoverAreaTracker();
  private final ClickListener clickListener = new ClickListener();

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new ToolbarComboWidgetUI();
  }

  @Override
  public void installUI(JComponent c) {
    ToolbarComboWidget widget = (ToolbarComboWidget)c;
    setUIDefaults(widget);
    hoverTracker.installTo(widget);
    clickListener.installTo(widget);
  }

  @Override
  public void uninstallUI(JComponent c) {
    hoverTracker.uninstall();
    clickListener.uninstall();
    c.removeMouseListener(clickListener);
  }

  private static void setUIDefaults(ToolbarComboWidget c) {
    c.setBackground(UIManager.getColor("ToolbarComboWidget.background"));
    c.setHoverBackground(UIManager.getColor("ToolbarComboWidget.hoverBackground"));

    Insets insets = UIManager.getInsets("ToolbarComboWidget.borderInsets");
    JBEmptyBorder border = JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right);
    c.setBorder(border);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    ToolbarComboWidget combo = (ToolbarComboWidget)c;
    if (c.isOpaque()) paintBackground(g, combo);

    List<Icon> leftIcons = combo.getLeftIcons();
    List<Icon> rightIcons = combo.getRightIcons();

    Rectangle paintRect = SwingUtilities.calculateInnerArea(c, null);

    Graphics2D g2 = (Graphics2D)g.create(paintRect.x, paintRect.y, paintRect.width, paintRect.height);
    try {
      if (!leftIcons.isEmpty()) {
        Rectangle iconsRect = paintIcons(leftIcons, combo, g2);
        doClip(g2, iconsRect.width + ELEMENTS_GAP);
      }

      String text = combo.getText();
      if (!StringUtil.isEmpty(text)) {
        int maxTextWidth = calcMaxTextWidth(combo, paintRect);
        g2.setColor(c.getForeground());
        Rectangle textRect = drawText(c, text, maxTextWidth, g2);
        doClip(g2, textRect.width + ELEMENTS_GAP);
      }

      if (!rightIcons.isEmpty()) {
        Rectangle iconsRect = paintIcons(rightIcons, combo, g2);
        doClip(g2, iconsRect.width + ELEMENTS_GAP);
      }

      if (isSeparatorShown(combo)) {
        g2.setColor(UIManager.getColor("Separator.separatorColor"));
        Rectangle bounds = g2.getClipBounds();
        g2.fillRect(bounds.x, bounds.y, SEPARATOR_WIDTH, bounds.height);
        doClip(g2, SEPARATOR_WIDTH + ELEMENTS_GAP);
      }

      paintIcons(Collections.singletonList(EXPAND_ICON), combo, g2);
    }
    finally {
      g2.dispose();
    }
  }

  private void paintBackground(Graphics g, ToolbarComboWidget c) {
    Graphics g2 = g.create();
    try {
      g2.setColor(c.getBackground());
      Rectangle bounds = g2.getClipBounds();
      g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);

      Rectangle hoverRect = hoverTracker.getHoverRect();
      if (hoverRect != null) {
        g2.setColor(c.getHoverBackground());
        g2.fillRect(hoverRect.x, hoverRect.y, hoverRect.width, hoverRect.height);
      }
    }
    finally {
      g2.dispose();
    }

  }

  private static Rectangle drawText(JComponent c, @NotNull String fullText, int maxWidth, Graphics2D g) {
    FontMetrics metrics = g.getFontMetrics();
    Rectangle clipBounds = g.getClipBounds();
    clipBounds.width = maxWidth;

    String text = calcShownText(fullText, metrics, maxWidth);
    Rectangle strBounds = metrics.getStringBounds(text, g).getBounds();
    strBounds.setLocation((int)(clipBounds.getCenterX() - strBounds.getCenterX()),
                          (int)(clipBounds.getCenterY() - strBounds.getCenterY()));

    SwingUtilities2.drawString(c, g, text, strBounds.x, strBounds.y);
    return clipBounds;
  }

  private static String calcShownText(String text, FontMetrics metrics, int maxWidth) {
    int width = metrics.stringWidth(text);
    while (width > maxWidth && text.length() > MIN_TEXT_LENGTH) {
      text = text.substring(0, text.length() - 1);
      width = metrics.stringWidth(text + "...");
    }

    return text;
  }

  private static int calcMaxTextWidth(ToolbarComboWidget c, Rectangle paintRect) {
    int left = calcIconsWidth(c.getLeftIcons());
    if (left > 0) left += ELEMENTS_GAP;

    int right = calcIconsWidth(c.getRightIcons());
    if (right > 0) right += ELEMENTS_GAP;

    int separator = isSeparatorShown(c) ? ELEMENTS_GAP + SEPARATOR_WIDTH : 0;

    int otherElementsWidth = left + right + separator + ELEMENTS_GAP + EXPAND_ICON.getIconWidth();
    return paintRect.width - otherElementsWidth;
  }

  private static int calcIconsWidth(List<Icon> icons) {
    int res = 0;
    for (Icon icon : icons) {
      if (res > 0) res += ICONS_GAP;
      res += icon.getIconWidth();
    }
    return res;
  }

  private static void doClip(Graphics2D g, int shift) {
    Rectangle bounds = g.getClipBounds();
    g.clipRect(bounds.x + shift, bounds.y, bounds.width - shift, bounds.height);
  }

  private static Rectangle paintIcons(List<Icon> icons, JComponent c, Graphics g) {
    if (icons.isEmpty()) return new Rectangle();

    Rectangle bounds = g.getClipBounds();
    int maxHeight = 0;
    int shift = 0;
    for (Icon icon : icons) {
      if (shift != 0) shift += ICONS_GAP;

      int x = bounds.x + shift;
      int y = bounds.y + bounds.height / 2 - icon.getIconHeight() / 2;
      icon.paintIcon(c, g, x, y);

      shift += icon.getIconWidth();
      maxHeight = Math.max(maxHeight, icon.getIconHeight());
    }

    return new Rectangle(shift, maxHeight);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    ToolbarComboWidget combo = (ToolbarComboWidget)c;
    Dimension res = new Dimension();

    List<Icon> icons = combo.getLeftIcons();
    if (!icons.isEmpty()) {
      res.width += calcIconsWidth(icons);
      res.height = icons.stream().mapToInt(Icon::getIconHeight).max().orElse(0);
    }

    if (!StringUtil.isEmpty(combo.getText())) {
      if (res.width > 0) res.width += ELEMENTS_GAP;
      FontMetrics metrics = c.getFontMetrics(c.getFont());
      res.width += metrics.stringWidth(combo.getText());
      res.height = Math.max(res.height, metrics.getHeight());
    }

    icons = combo.getRightIcons();
    if (!icons.isEmpty()) {
      if (res.width > 0) res.width += ELEMENTS_GAP;
      res.width += calcIconsWidth(icons);
      res.height = Math.max(res.height, icons.stream().mapToInt(Icon::getIconHeight).max().orElse(0));
    }

    if (isSeparatorShown(combo)) {
      if (res.width > 0) res.width += ELEMENTS_GAP;
      res.width += SEPARATOR_WIDTH;
    }

    if (res.width > 0) res.width += ELEMENTS_GAP;
    res.width += EXPAND_ICON.getIconWidth();
    res.height = Math.max(res.height, EXPAND_ICON.getIconHeight());

    Insets insets = c.getInsets();
    res.height += insets.top + insets.bottom;
    res.width += insets.left + insets.right;

    return res;
  }

  private static boolean isSeparatorShown(ToolbarComboWidget widget) {
    return !widget.getPressListeners().isEmpty();
  }

  //todo minimum size
  //todo baseline

  private static abstract class MyMouseTracker extends MouseAdapter {
    protected ToolbarComboWidget comp;

    public void installTo(ToolbarComboWidget c) {
      comp = c;
      c.addMouseListener(this);
      c.addMouseMotionListener(this);
    }

    public void uninstall() {
      comp.removeMouseListener(this);
      comp.removeMouseMotionListener(this);
      comp = null;
    }
  }

  private static class HoverAreaTracker extends MyMouseTracker {

    private Rectangle hoverRect;

    private Rectangle getHoverRect() {
      return hoverRect;
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      calcHoverRect(e.getPoint());
    }

    @Override
    public void mouseExited(MouseEvent e) {
      updateHoverRect(null);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      calcHoverRect(e.getPoint());
    }

    private void calcHoverRect(Point mousePosition) {
      Rectangle compBounds = comp.getVisibleRect();
      if (!isSeparatorShown(comp)) {
        updateHoverRect(compBounds);
        return;
      }

      int rightPart = SEPARATOR_WIDTH + ELEMENTS_GAP + EXPAND_ICON.getIconWidth() + comp.getInsets().right;
      Rectangle right = new Rectangle((int)(compBounds.getMaxX() - rightPart), compBounds.y, rightPart, compBounds.height);
      Rectangle left = new Rectangle(compBounds.x, compBounds.y, compBounds.width - rightPart + SEPARATOR_WIDTH, compBounds.height);

      updateHoverRect(left.contains(mousePosition) ? left : right);
    }

    private void updateHoverRect(Rectangle newRect) {
      if (Objects.equals(hoverRect, newRect)) return;
      hoverRect = newRect;
      comp.repaint();
    }
  }

  private static class ClickListener extends MyMouseTracker {

    @Override
    public void mouseClicked(MouseEvent e) {
      if (!isSeparatorShown(comp)) {
        comp.doExpand(e);
        return;
      }

      int leftPartWidth = comp.getWidth() - (ELEMENTS_GAP + EXPAND_ICON.getIconWidth() + comp.getInsets().right);
      if (e.getPoint().x <= leftPartWidth) notifyPressListeners(e);
      else comp.doExpand(e);
    }

    private void notifyPressListeners(MouseEvent e) {
      ActionEvent ae = new ActionEvent(comp, 0, null, System.currentTimeMillis(), e.getModifiersEx());
      comp.getPressListeners().forEach(listener -> listener.actionPerformed(ae));
    }
  }
}
