// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ToolbarComboWidget;
import com.intellij.ui.JBColor;
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
    c.setForeground(JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground()));
    c.setBackground(JBColor.namedColor("MainToolbar.Dropdown.background", JBColor.foreground()));
    c.setHoverBackground(JBColor.namedColor("MainToolbar.Dropdown.hoverBackground", JBColor.background()));

    Insets insets = UIManager.getInsets("MainToolbar.Dropdown.borderInsets");
    JBEmptyBorder border = JBUI.Borders.empty(insets.top, insets.left, insets.bottom, insets.right);
    c.setBorder(border);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    ToolbarComboWidget combo = (ToolbarComboWidget)c;
    paintBackground(g, combo);

    List<Icon> leftIcons = combo.getLeftIcons();
    List<Icon> rightIcons = combo.getRightIcons();

    Rectangle innerArea = SwingUtilities.calculateInnerArea(c, null);
    Graphics2D g2 = (Graphics2D)g.create(innerArea.x, innerArea.y, innerArea.width, innerArea.height);
    Rectangle paintRect = new Rectangle(0, 0, innerArea.width, innerArea.height);
    int maxTextWidth = calcMaxTextWidth(combo, paintRect);
    try {
      if (!leftIcons.isEmpty()) {
        Rectangle iconsRect = paintIcons(leftIcons, combo, g2, paintRect, combo.getLeftIconsGap());
        doClip(paintRect, iconsRect.width + ELEMENTS_GAP);
      }

      String text = combo.getText();
      if (!StringUtil.isEmpty(text)) {
        g2.setColor(c.getForeground());
        Rectangle textRect = new Rectangle(paintRect.x, paintRect.y, maxTextWidth, paintRect.height);
        drawText(c, text, g2, textRect);
        doClip(paintRect, maxTextWidth + ELEMENTS_GAP);
      }

      if (!rightIcons.isEmpty()) {
        Rectangle iconsRect = paintIcons(rightIcons, combo, g2, paintRect, combo.getRightIconsGap());
        doClip(paintRect, iconsRect.width + ELEMENTS_GAP);
      }

      if (isSeparatorShown(combo)) {
        g2.setColor(UIManager.getColor("Separator.separatorColor"));
        g2.fillRect(paintRect.x, paintRect.y, SEPARATOR_WIDTH, paintRect.height);
        doClip(paintRect, SEPARATOR_WIDTH + ELEMENTS_GAP);
      }
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  public int getBaseline(JComponent c, int width, int height) {
    super.getBaseline(c, width, height);
    ToolbarComboWidget widget = (ToolbarComboWidget)c;
    Rectangle iconRect = new Rectangle();
    Rectangle textRect = new Rectangle();
    SwingUtilities.layoutCompoundLabel(c, c.getFontMetrics(c.getFont()), widget.getText(), null,
                                       SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                       new Rectangle(width, height), iconRect, textRect, 0);
    FontMetrics fm = c.getFontMetrics(c.getFont());
    return textRect.y + fm.getAscent();
  }

  private void paintBackground(Graphics g, ToolbarComboWidget c) {
    Graphics g2 = g.create();
    try {
      if (c.isOpaque()) {
        g2.setColor(c.getBackground());
        Rectangle bounds = g2.getClipBounds();
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }

      Rectangle hoverRect = hoverTracker.getHoverRect();
      Color hoverBackground = c.getHoverBackground();
      if (hoverRect != null && hoverBackground != null) {
        g2.setColor(hoverBackground);
        g2.fillRect(hoverRect.x, hoverRect.y, hoverRect.width, hoverRect.height);
      }
    }
    finally {
      g2.dispose();
    }
  }

  private static void drawText(JComponent c, @NotNull String fullText, Graphics2D g, Rectangle textBounds) {
    FontMetrics metrics = c.getFontMetrics(c.getFont());

    int baseline = c.getBaseline(textBounds.width, textBounds.height);
    String text = calcShownText(fullText, metrics, textBounds.width);
    Rectangle strBounds = metrics.getStringBounds(text, g).getBounds();
    strBounds.setLocation((int)(textBounds.getCenterX() - strBounds.getCenterX()), baseline);
    SwingUtilities2.drawString(c, g, text, strBounds.x, strBounds.y);
  }

  private static String calcShownText(String text, FontMetrics metrics, int maxWidth) {
    int width = metrics.stringWidth(text);
    if (width <= maxWidth) return text;

    while (width > maxWidth && text.length() > MIN_TEXT_LENGTH) {
      text = text.substring(0, text.length() - 1);
      width = metrics.stringWidth(text + "...");
    }
    return text + "...";
  }

  private static int calcMaxTextWidth(ToolbarComboWidget c, Rectangle paintRect) {
    int left = calcIconsWidth(c.getLeftIcons(), c.getLeftIconsGap());
    if (left > 0) left += ELEMENTS_GAP;

    int right = calcIconsWidth(c.getRightIcons(), c.getRightIconsGap());
    if (right > 0) right += ELEMENTS_GAP;

    int separator = isSeparatorShown(c) ? ELEMENTS_GAP + SEPARATOR_WIDTH : 0;

    int otherElementsWidth = left + right + separator + ELEMENTS_GAP;
    return paintRect.width - otherElementsWidth;
  }

  private static int calcIconsWidth(List<Icon> icons, int gapBetweenIcons) {
    int res = 0;
    for (Icon icon : icons) {
      if (res > 0) res += gapBetweenIcons;
      res += icon.getIconWidth();
    }
    return res;
  }

  private static void doClip(Rectangle bounds, int shift) {
    bounds.setBounds(bounds.x + shift, bounds.y, bounds.width - shift, bounds.height);
  }

  private static Rectangle paintIcons(List<Icon> icons, JComponent c, Graphics g, Rectangle bounds, int gapBetweenIcons) {
    if (icons.isEmpty()) return new Rectangle();

    int maxHeight = 0;
    int shift = 0;
    for (Icon icon : icons) {
      if (shift != 0) shift += gapBetweenIcons;

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
      res.width += calcIconsWidth(icons, combo.getLeftIconsGap());
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
      res.width += calcIconsWidth(icons, combo.getRightIconsGap());
      res.height = Math.max(res.height, icons.stream().mapToInt(Icon::getIconHeight).max().orElse(0));
    }

    if (isSeparatorShown(combo)) {
      if (res.width > 0) res.width += ELEMENTS_GAP;
      res.width += SEPARATOR_WIDTH;
    }

    if (res.width > 0) res.width += ELEMENTS_GAP;

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

      int rightPart = SEPARATOR_WIDTH + ELEMENTS_GAP + comp.getInsets().right;

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

      int leftPartWidth = comp.getWidth() - (ELEMENTS_GAP + comp.getInsets().right);
      if (e.getPoint().x <= leftPartWidth) {
        notifyPressListeners(e);
      }
      else {
        comp.doExpand(e);
      }
    }

    private void notifyPressListeners(MouseEvent e) {
      ActionEvent ae = new ActionEvent(comp, 0, null, System.currentTimeMillis(), e.getModifiersEx());
      comp.getPressListeners().forEach(listener -> listener.actionPerformed(ae));
    }
  }
}
