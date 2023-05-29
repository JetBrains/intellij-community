// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ProjectWindowCustomizerService;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.ToolbarComboWidget;
import com.intellij.ui.ClickListener;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.BadLocationException;
import javax.swing.text.View;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.ide.ui.laf.darcula.ui.ToolbarComboWidgetUiSizes.*;

public class ToolbarComboWidgetUI extends ComponentUI implements PropertyChangeListener {
  private static final Icon EXPAND_ICON = AllIcons.General.ChevronDown;
  private static final int SEPARATOR_WIDTH = 1;
  private static final int SEPARATOR_HEIGHT = 20;
  private static final int DEFAULT_MAX_WIDTH = 350;

  private final HoverAreaTracker hoverTracker = new HoverAreaTracker();
  private final ClickListener clickListener = new ToolbarComboWidgetClickListener();
  private TextCutStrategy textCutStrategy = new DefaultCutStrategy();
  private int maxWidth;

  private int separatorPosition = 0;

  public ToolbarComboWidgetUI() {
    maxWidth = UIManager.getInt("MainToolbar.Dropdown.maxWidth");
    if (maxWidth == 0) maxWidth = DEFAULT_MAX_WIDTH;
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(JComponent c) {
    return new ToolbarComboWidgetUI();
  }

  @Override
  public void installUI(JComponent c) {
    ToolbarComboWidget widget = (ToolbarComboWidget)c;
    setUIDefaults(widget);
    widget.addPropertyChangeListener(this);
    tryUpdateHtmlRenderer(widget, widget.getText());
    hoverTracker.installTo(widget);
    clickListener.installOn(widget);
  }

  @Override
  public void uninstallUI(JComponent c) {
    ToolbarComboWidget widget = (ToolbarComboWidget)c;
    widget.removePropertyChangeListener(this);
    tryUpdateHtmlRenderer(widget, "");
    hoverTracker.uninstall();
    clickListener.uninstall(widget);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (Objects.equals(evt.getPropertyName(), "text") || Objects.equals(evt.getPropertyName(), "font")) {
      ToolbarComboWidget widget = (ToolbarComboWidget)evt.getSource();
      tryUpdateHtmlRenderer(widget, widget.getText());
    }
  }

  private void tryUpdateHtmlRenderer(ToolbarComboWidget widget, String text) {
    if (widget.getFont() == null && BasicHTML.isHTMLString(text))
      return;
    BasicHTML.updateRenderer(widget, text);
  }

  private static void setUIDefaults(ToolbarComboWidget c) {
    c.setForeground(JBColor.namedColor("MainToolbar.Dropdown.foreground", JBColor.foreground()));
    c.setBackground(JBColor.namedColor("MainToolbar.Dropdown.background", JBColor.background()));
    c.setHoverBackground(JBColor.namedColor("MainToolbar.Dropdown.hoverBackground", JBColor.background()));
    c.setTransparentHoverBackground(JBColor.namedColor("MainToolbar.Dropdown.transparentHoverBackground", c.getHoverBackground()));

    Insets insets = JBUI.CurrentTheme.MainToolbar.Dropdown.borderInsets();
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
      GraphicsUtil.setupAAPainting(g2);
      if (!leftIcons.isEmpty()) {
        Rectangle iconsRect = paintIcons(leftIcons, combo, g2, paintRect, combo.getLeftIconsGap());
        doClip(paintRect, iconsRect.width + getGapAfterLeftIcons());
      }

      String text = getText(combo);
      if (!StringUtil.isEmpty(text) && maxTextWidth > 0) {
        Rectangle textRect = new Rectangle(paintRect.x, paintRect.y, maxTextWidth, paintRect.height);
        drawText(c, text, g2, textRect);
        doClip(paintRect, maxTextWidth);
      }

      if (!rightIcons.isEmpty()) {
        doClip(paintRect, getGapBeforeRightIcons());
        Rectangle iconsRect = paintIcons(rightIcons, combo, g2, paintRect, combo.getRightIconsGap());
        doClip(paintRect, iconsRect.width);
      }

      if (isSeparatorShown(combo)) {
        doClip(paintRect, getGapBeforeSeparator());
        g2.setColor(c.isEnabled() ? UIManager.getColor("MainToolbar.separatorColor") : UIUtil.getLabelDisabledForeground());
        g2.fillRect(paintRect.x, ((int)paintRect.getCenterY()) - SEPARATOR_HEIGHT / 2, SEPARATOR_WIDTH, SEPARATOR_HEIGHT);
        separatorPosition = paintRect.x;
        doClip(paintRect, SEPARATOR_WIDTH);
      }

      if (combo.isExpandable()) {
        doClip(paintRect, getGapBeforeExpandIcon());
        paintIcons(Collections.singletonList(EXPAND_ICON), combo, g2, paintRect, 0); // no gap for single icon
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
    SwingUtilities.layoutCompoundLabel(c, c.getFontMetrics(c.getFont()), getText(widget), null,
                                       SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                       new Rectangle(width, height), iconRect, textRect, 0);
    FontMetrics fm = c.getFontMetrics(c.getFont());
    return textRect.y + fm.getAscent();
  }

  public void setTextCutStrategy(TextCutStrategy strategy) {
    textCutStrategy = strategy;
  }

  public void setMaxWidth(int width) {
    maxWidth = width;
  }

  private void paintBackground(Graphics g, ToolbarComboWidget c) {
    Graphics g2 = g.create();
    try {
      if (c.isOpaque()) {
        g2.setColor(c.getBackground());
        Rectangle bounds = g2.getClipBounds();
        g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }

      if (c.isEnabled()) {
        Rectangle hoverRect = hoverTracker.getHoverRect();
        Color hoverBackground = ProjectWindowCustomizerService.getInstance().isActive()
                                ? c.getTransparentHoverBackground() : c.getHoverBackground();
        if (hoverRect != null && hoverBackground != null) {
          g2.setColor(hoverBackground);
          g2.fillRect(hoverRect.x, hoverRect.y, hoverRect.width, hoverRect.height);
        }
      }
    }
    finally {
      g2.dispose();
    }
  }

  private void drawText(JComponent c, @NotNull String fullText, Graphics2D g, Rectangle textBounds) {
    FontMetrics metrics = c.getFontMetrics(c.getFont());
    g.setColor(c.isEnabled() ? c.getForeground() : UIUtil.getLabelDisabledForeground());

    int baseline = c.getBaseline(textBounds.width, textBounds.height);
    String text = textCutStrategy.calcShownText(fullText, metrics, textBounds.width);
    Rectangle strBounds = metrics.getStringBounds(text, g).getBounds();
    strBounds.setLocation(Math.max(0, (int)(textBounds.getCenterX() - strBounds.getCenterX())), baseline);

    View v = (View)c.getClientProperty(BasicHTML.propertyKey);
    if (v != null) {
      strBounds.y -= metrics.getAscent();
      v.paint(g, strBounds);
    }
    else {
      SwingUtilities2.drawString(c, g, text, strBounds.x, strBounds.y);
    }
  }

  private static int calcMaxTextWidth(ToolbarComboWidget c, Rectangle paintRect) {
    int left = calcIconsWidth(c.getLeftIcons(), c.getLeftIconsGap());
    if (left > 0) left += getGapAfterLeftIcons();

    int right = calcIconsWidth(c.getRightIcons(), c.getRightIconsGap());
    if (right > 0) right += getGapBeforeRightIcons();

    int separator = isSeparatorShown(c) ? getGapBeforeSeparator() + SEPARATOR_WIDTH : 0;
    int expandButton = c.isExpandable() ? getGapBeforeExpandIcon() + EXPAND_ICON.getIconWidth() : 0;

    int otherElementsWidth = left + right + separator + expandButton;
    return paintRect.width - otherElementsWidth;
  }

  private static int calcIconsWidth(List<? extends Icon> icons, int gapBetweenIcons) {
    int res = 0;
    for (Icon icon : icons) {
      if (res > 0) res += gapBetweenIcons;
      res += icon.getIconWidth();
    }
    return res;
  }

  private static void doClip(Rectangle bounds, int shift) {
    bounds.setBounds(bounds.x + shift, bounds.y, Math.max(bounds.width - shift, 0), bounds.height);
  }

  private static Rectangle paintIcons(List<? extends Icon> icons, JComponent c, Graphics g, Rectangle bounds, int gapBetweenIcons) {
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
      res.width += calcIconsWidth(icons, combo.getLeftIconsGap()) + getGapAfterLeftIcons();
      res.height = icons.stream().mapToInt(Icon::getIconHeight).max().orElse(0);
    }

    if (!StringUtil.isEmpty(combo.getText())) {
      FontMetrics metrics = c.getFontMetrics(c.getFont());
      String text = getText(combo);
      res.width += metrics.stringWidth(text);
      res.height = Math.max(res.height, metrics.getHeight());
    }

    icons = combo.getRightIcons();
    if (!icons.isEmpty()) {
      if (res.width > 0) res.width += getGapBeforeSeparator();
      res.width += calcIconsWidth(icons, combo.getRightIconsGap());
      res.height = Math.max(res.height, icons.stream().mapToInt(Icon::getIconHeight).max().orElse(0));
    }

    if (isSeparatorShown(combo)) {
      if (res.width > 0) res.width += getGapBeforeSeparator();
      res.width += SEPARATOR_WIDTH + getGapBeforeSeparator();
    }

    if (combo.isExpandable()) {
      if (res.width > 0) res.width += getGapBeforeExpandIcon();
      res.width += EXPAND_ICON.getIconWidth();
      res.height = Math.max(res.height, EXPAND_ICON.getIconHeight());
    }

    Insets insets = c.getInsets();
    res.height += insets.top + insets.bottom;
    res.width += insets.left + insets.right;

    res.width = Integer.min(maxWidth, res.width);
    return res;
  }

  private @Nls String getText(ToolbarComboWidget widget) {
    View v = (View)widget.getClientProperty(BasicHTML.propertyKey);
    if (v != null) {
      try {
        @NlsSafe String text = (v.getDocument().getText(0, v.getDocument().getLength())).strip();
        return text;
      }
      catch (BadLocationException ignored) {
      }
    }

    return widget.getText();
  }

  private static boolean isSeparatorShown(ToolbarComboWidget widget) {
    return !widget.getPressListeners().isEmpty() && widget.isExpandable();
  }

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

  private class HoverAreaTracker extends MyMouseTracker {

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

      Insets insets = comp.getInsets();
      Rectangle left = new Rectangle(compBounds.x, compBounds.y, separatorPosition + insets.left + SEPARATOR_WIDTH, compBounds.height);
      Rectangle right = new Rectangle(separatorPosition + insets.left, compBounds.y, (int)(compBounds.getMaxX() - insets.right - separatorPosition), compBounds.height);

      updateHoverRect(left.contains(mousePosition) ? left : right);
    }

    private void updateHoverRect(Rectangle newRect) {
      if (Objects.equals(hoverRect, newRect)) return;
      hoverRect = newRect;
      comp.repaint();
    }
  }

  private static class ToolbarComboWidgetClickListener extends ClickListener {
    private static void notifyPressListeners(MouseEvent e) {
      ToolbarComboWidget comp = (ToolbarComboWidget)e.getComponent();
      ActionEvent ae = new ActionEvent(comp, 0, null, System.currentTimeMillis(), e.getModifiersEx());
      comp.getPressListeners().forEach(listener -> listener.actionPerformed(ae));
    }

    @Override
    public boolean onClick(@NotNull MouseEvent e, int clickCount) {
      Component component = e.getComponent();
      if (!(component instanceof ToolbarComboWidget comp)) return false;
      if (!comp.isEnabled()) return false;
      if (!comp.isExpandable()) {
        notifyPressListeners(e);
        return false;
      }

      if (!isSeparatorShown(comp)) {
        comp.doExpand(e);
        return true;
      }

      int leftPartWidth = comp.getWidth() - (getGapBeforeExpandIcon() + EXPAND_ICON.getIconWidth() + comp.getInsets().right);
      if (e.getPoint().x <= leftPartWidth) {
        notifyPressListeners(e);
        return false;
      }
      else {
        comp.doExpand(e);
        return true;
      }
    }
  }

  private static class DefaultCutStrategy implements TextCutStrategy {

    private static final int MIN_TEXT_LENGTH = 5;

    @NotNull
    @Override
    public String calcShownText(@NotNull String text, @NotNull FontMetrics metrics, int maxWidth) {
      int width = metrics.stringWidth(text);
      if (width <= maxWidth) return text;

      while (width > maxWidth && text.length() > MIN_TEXT_LENGTH) {
        text = text.substring(0, text.length() - 1);
        width = metrics.stringWidth(text + "...");
      }
      return text + "...";
    }
  }
}
