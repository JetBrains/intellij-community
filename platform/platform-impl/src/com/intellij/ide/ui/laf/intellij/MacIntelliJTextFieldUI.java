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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.MacUIUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
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
    return new Rectangle(0, (myTextField.getHeight() - 28) / 2, myTextField.getWidth(), myTextField.getHeight());
  }

  private static Icon getSearchIcon(Component c) {
    return MacIntelliJIconCache.getIcon(isSearchFieldWithHistoryPopup(c) ? "searchFieldWithHistory" : "search");
  }

  protected Point getSearchIconCoord() {
    final Rectangle r = getDrawingRect();
    Icon icon = getSearchIcon(myTextField);
    return new Point(r.x + (hasText() || myTextField.hasFocus() || isSearchFieldWithHistoryPopup(myTextField)
                            ? JBUI.scale(8)
                            : (r.width - icon.getIconWidth()) / 2),
      r.y + (r.height - icon.getIconHeight()) / 2);
  }

  protected Point getClearIconCoord() {
    Rectangle r = getDrawingRect();
    Icon icon = MacIntelliJIconCache.getIcon("searchFieldClear");
    return new Point(r.x + r.width - icon.getIconWidth() - JBUI.scale(6), r.y + (r.height - icon.getIconHeight()) / 2);
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
        g.fillRect(JBUI.scale(3), JBUI.scale(3), c.getWidth() - JBUI.scale(6), c.getHeight() - JBUI.scale(6));
      }
      else {
        super.paintBackground(g);
      }
      paintIcons(g);
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    return new Dimension(size.width + getIconsWidth(c), Math.max(28, size.height));
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
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      int arc = JBUI.scale(6);
      double lw = UIUtil.isRetina(g2) ? 0.5 : 1.0;
      Shape outerShape = new RoundRectangle2D.Double(JBUI.scale(3), JBUI.scale(3),
                                                     r.width - JBUI.scale(6),
                                                     r.height - JBUI.scale(6),
                                                     arc, arc);
      g2.setColor(c.getBackground());
      g2.fill(outerShape);

      Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Double(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                              r.width - JBUI.scale(6) - lw*2,
                                              r.height - JBUI.scale(6) - lw*2,
                                              arc-lw, arc-lw), false);

      g2.setColor(Gray.xBC);
      g2.fill(path);

      if (c.hasFocus() && c.getClientProperty("JTextField.Search.noBorderRing") != Boolean.TRUE) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
      }

      g2.translate(-r.x, -r.y);

      boolean withHistoryPopup = isSearchFieldWithHistoryPopup(c);
      Icon label = getSearchIcon(c);
      boolean isEmpty = !hasText();
      Point point = getSearchIconCoord();
      if (isEmpty && !c.hasFocus() && !withHistoryPopup) {
        label.paintIcon(c, g2, point.x, point.y);
      } else {
        Graphics ig = g2.create(0, 0, c.getWidth(), c.getHeight());

        Area area = new Area(new Rectangle2D.Double(point.x, point.y, isEmpty ? label.getIconWidth() : 16, label.getIconHeight()));
        area.intersect(new Area(ig.getClip()));
        ig.setClip(area);
        label.paintIcon(c, ig, point.x, point.y);
        ig.dispose();
      }

      if (!isEmpty) {
        Point ic = getClearIconCoord();
        MacIntelliJIconCache.getIcon("searchFieldClear").paintIcon(c, g2, ic.x, ic.y);
      }

      AbstractAction newLineAction = getNewLineAction(c);
      if (newLineAction != null) {
        Icon newLineIcon = (Icon)newLineAction.getValue(Action.SMALL_ICON);
        if (newLineIcon != null) {
          newLineIcon.paintIcon(c, g2, getAddNewLineIconCoord().x, r.y);
        }
      }
    } finally {
      g2.dispose();
    }
  }

  @Override
  protected void updateVisibleEditorRect(Rectangle rect) {
    if (isSearchField(myTextField)) {
      int extraOffset = isSearchFieldWithHistoryPopup(myTextField) ? 3 : 0;
      rect.width -= 36 + extraOffset;
      if (getNewLineAction(myTextField) != null) rect.width -= 24;
      rect.x += 19 + extraOffset;
      if (rect.height % 2 == 1) {
        rect.y += 1;
      }
    }
    else {
      rect.x += 2;
      rect.width -= 4;
      super.updateVisibleEditorRect(rect);
    }
  }

  @Override
  protected void paintSafely(Graphics g) {
    paintBackground(g);
    super.paintSafely(g);
  }

  public static void paintAquaSearchFocusRing(Graphics2D g, Rectangle r, Component c) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, MacUIUtil.USE_QUARTZ ? RenderingHints.VALUE_STROKE_PURE : RenderingHints.VALUE_STROKE_NORMALIZE);
      g2.translate(r.x, r.y);

      int arc = JBUI.scale(6);
      double lw = UIUtil.isRetina(g2) ? 0.5 : 1.0;
      Shape outerShape = new RoundRectangle2D.Double(JBUI.scale(3), JBUI.scale(3),
                                                     r.width - JBUI.scale(6),
                                                     r.height - JBUI.scale(6),
                                                     arc, arc);
      g2.setColor(c.getBackground());
      g2.fill(outerShape);

      Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
      path.append(outerShape, false);
      path.append(new RoundRectangle2D.Double(JBUI.scale(3) + lw, JBUI.scale(3) + lw,
                                              r.width - JBUI.scale(6) - lw*2,
                                              r.height - JBUI.scale(6) - lw*2,
                                              arc-lw, arc-lw), false);

      g2.setColor(Gray.xBC);
      g2.fill(path);

      if (c.hasFocus()) {
        DarculaUIUtil.paintFocusBorder(g2, r.width, r.height, arc, true);
      }
    }
    finally {
      g2.dispose();
    }
  }
}
