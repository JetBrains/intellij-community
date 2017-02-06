/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextFieldUI extends TextFieldWithPopupHandlerUI {

  public MacIntelliJTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new MacIntelliJTextFieldUI((JTextField)c);
  }

  public void showSearchPopup() {
    final Object value = myTextField.getClientProperty("JTextField.Search.FindPopup");
    final JTextComponent editor = getComponent();
    if (editor != null && value instanceof JPopupMenu) {
      final JPopupMenu popup = (JPopupMenu)value;
      popup.show(editor, getSearchIconCoord().x, editor.getHeight());
    }
  }

  @Override
  public String getToolTipText(JTextComponent t, Point pt) {
    if (getActionUnder(pt) == SearchAction.NEWLINE) {
      AbstractAction action = getNewLineAction(t);
      if (action != null) return (String)action.getValue(Action.SHORT_DESCRIPTION);
    }
    return super.getToolTipText(t, pt);
  }

  public SearchAction getActionUnder(@NotNull Point p) {
    int off = JBUI.scale(8);
    Point point = new Point(p.x - off, p.y - off);
    if (point.distance(getSearchIconCoord()) <= off) {
      return SearchAction.POPUP;
    }
    if (hasText() && point.distance(getClearIconCoord()) <= off) {
      return SearchAction.CLEAR;
    }
    if (getNewLineAction(myTextField) != null && point.distance(getAddNewLineIconCoord()) <= off) {
      return SearchAction.NEWLINE;
    }
    return null;
  }

  protected Rectangle getDrawingRect() {
    return new Rectangle(0, (myTextField.getHeight() - 26) / 2, myTextField.getWidth(), myTextField.getHeight());
  }

  Icon getSearchIcon(Component c) {
    return MacIntelliJIconCache.getIcon(isSearchFieldWithHistoryPopup(c) ? "searchFieldWithHistory" : "searchFieldLabel");
  }

  protected Point getSearchIconCoord() {
    final Rectangle r = getDrawingRect();
    Icon icon = getSearchIcon(myTextField);
    return new Point(r.x + (hasText() || myTextField.hasFocus() || isSearchFieldWithHistoryPopup(myTextField)
                            ? JBUI.scale(8)
                            : (r.width - icon.getIconWidth()) / 2)
      , r.y + (r.height - icon.getIconHeight()) / 2 + JBUI.scale(1));
  }

  protected Point getClearIconCoord() {
    final Rectangle r = getDrawingRect();
    return new Point(r.x + r.width - JBUI.scale(16) - JBUI.scale(6), r.y + (r.height - JBUI.scale(16)) / 2);
  }

  protected Point getAddNewLineIconCoord() {
    Point point = getClearIconCoord();
    if (!StringUtil.isEmpty(myTextField.getText())) point.x -= JBUI.scale(16) + JBUI.scale(8);
    return point;
  }

  @Override
  protected void paintBackground(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics;
    final JTextComponent c = getComponent();
    final Container parent = c.getParent();
    final Rectangle r = getDrawingRect();
    if (c.isOpaque() && parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }

    if (isSearchField(c)) {
      paintSearchField(g, c, r);
    }
    else {
      if (c.getBorder() instanceof MacIntelliJTextBorder) {
        g.setColor(c.getBackground());
        g.fillRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
      }
      else {
        super.paintBackground(g);
      }
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    return new Dimension(size.width + getIconsWidth(c), Math.max(26, size.height));
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    Dimension minimumSize = super.getMinimumSize(c);
    return new Dimension(minimumSize.width + getIconsWidth(c), minimumSize.height);
  }

  private int getIconsWidth(JComponent c) {
    int width = 0;
    if (isSearchField(c)) {
      Icon label = getSearchIcon(c);
      width += label.getIconWidth();
      if (hasText()) {
        Icon clearIcon = MacIntelliJIconCache.getIcon("searchFieldClear");
        width += clearIcon.getIconWidth() + 3;
      }
    }
    return width;
  }

  protected void paintSearchField(Graphics2D g, JTextComponent c, Rectangle r) {
    final boolean noBorder = c.getClientProperty("JTextField.Search.noBorderRing") == Boolean.TRUE;
    boolean hasFocus = c.hasFocus() && !noBorder;
    Icon left = MacIntelliJIconCache.getIcon("searchFieldLeft", false, hasFocus);
    Icon middle = MacIntelliJIconCache.getIcon("searchFieldMiddle", false, hasFocus);
    Icon right = MacIntelliJIconCache.getIcon("searchFieldRight", false, hasFocus);
    Graphics gg = g.create(0, 0, c.getWidth(), c.getHeight());
    gg.setClip(r.x, r.y, r.width - right.getIconWidth(), r.height);
    int x = r.x;
    int stop = r.x + (r.width - right.getIconWidth());
    left.paintIcon(c, g, r.x, r.y);
    x += left.getIconWidth();
    while (x < stop) {
      middle.paintIcon(c, gg, x, r.y);
      x += middle.getIconWidth();
    }
    gg.dispose();
    right.paintIcon(c, g, stop, r.y);

    boolean withHistoryPopup = isSearchFieldWithHistoryPopup(c);
    Icon label = getSearchIcon(c);
    boolean isEmpty = !hasText();
    Point point = getSearchIconCoord();
    if (isEmpty && !c.hasFocus() && !withHistoryPopup) {
      label.paintIcon(c, g, point.x, point.y);
    }
    else {
      gg = g.create(0, 0, c.getWidth(), c.getHeight());
      gg.setClip(point.x, point.y, isEmpty ? label.getIconWidth() : 16, label.getIconHeight());
      label.paintIcon(c, gg, point.x, point.y);
    }

    Icon clearIcon = MacIntelliJIconCache.getIcon("searchFieldClear");
    if (!isEmpty) {
      clearIcon.paintIcon(c, g, getClearIconCoord().x, r.y);
    }
    AbstractAction newLineAction = getNewLineAction(c);
    if (newLineAction != null) {
      Icon newLineIcon = (Icon)newLineAction.getValue(Action.SMALL_ICON);
      if (newLineIcon != null) {
        newLineIcon.paintIcon(c, g, getAddNewLineIconCoord().x, r.y);
      }
    }
  }


  @Override
  protected Rectangle getVisibleEditorRect() {
    Rectangle rect = super.getVisibleEditorRect();
    if (rect != null) {
      if (isSearchField(myTextField)) {
        int extraOffset = isSearchFieldWithHistoryPopup(myTextField) ? 3 : 0;
        rect.width -= 36 + extraOffset;
        if (getNewLineAction(myTextField) != null) rect.width -= 24;
        rect.x += 19 + extraOffset;
        rect.y += 1;
      }
      else {
        rect.x += 2;
        rect.width -= 4;
      }
    }
    return rect;
  }

  @Override
  protected void paintSafely(Graphics g) {
    paintBackground(g);
    super.paintSafely(g);
  }

  public static void paintAquaSearchFocusRing(Graphics2D g, Rectangle r, Component c) {
    g = (Graphics2D)g.create(0, 0, r.width, r.height);
    GraphicsUtil.setupAAPainting(g);
    g.setColor(c.getBackground());
    RoundRectangle2D.Double border = getShape(r, 4, true);
    g.fill(border);

    g.setColor(Gray._192);
    g.setStroke(new BasicStroke(.5f));
    g.draw(getShape(r, 4, false));

    if (c.hasFocus()) {
      Color graphiteColor = new Color(0x6f6f72);
      Color blueColor = ColorUtil.brighter(new Color(0x006de2), 3);
      g.setColor(ColorUtil.withAlpha(IntelliJLaf.isGraphite() ? graphiteColor : blueColor, .35));
      Area area = new Area(getShape(r, 7, false));
      area.subtract(new Area(getShape(r, 3.5, true)));
      g.fill(area);
    }
    g.dispose();
  }

  private static RoundRectangle2D.Double getShape(Rectangle r, double radius, boolean inner) {
    double max_radius = 6;
    radius = Math.min(max_radius, Math.max(0, radius));
    double inset = max_radius - radius;
    double indent = inner ? 0.5 : 0;
    return new RoundRectangle2D.Double(r.x + inset + indent, r.y + inset + indent, r.width - 2 * inset - indent,
                                       r.height - 2 * inset - indent, 2 * radius, 2 * radius);
  }
}
