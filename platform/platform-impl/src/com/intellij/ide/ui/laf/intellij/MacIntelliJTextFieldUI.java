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

import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;

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

  private boolean hasText() {
    JTextComponent component = getComponent();
    return (component != null) && !StringUtil.isEmpty(component.getText());
  }

  public SearchAction getActionUnder(MouseEvent e) {
    int off = JBUI.scale(8);
    Point point = new Point(e.getX() - off, e.getY() - off);
    return point.distance(getSearchIconCoord()) <= off
           ? SearchAction.POPUP
           : hasText() && point.distance(getClearIconCoord()) <= off
             ? SearchAction.CLEAR
             : null;
  }

  protected Rectangle getDrawingRect() {
    return new Rectangle(0, (myTextField.getHeight() - 26) / 2, myTextField.getWidth(), myTextField.getHeight());
  }

  protected Point getSearchIconCoord() {
    final Rectangle r = getDrawingRect();
    return new Point(r.x + JBUI.scale(3), r.y + (r.height - JBUI.scale(16)) / 2 + JBUI.scale(1));
  }

  protected Point getClearIconCoord() {
    final Rectangle r = getDrawingRect();
    return new Point(r.x + r.width - JBUI.scale(16) - JBUI.scale(2), r.y + (r.height - JBUI.scale(16)) / 2);
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
    } else {
      if (c.getBorder() instanceof MacIntelliJTextBorder) {
        g.setColor(c.getBackground());
        g.fillRect(3, 3, c.getWidth() - 6, c.getHeight() - 6);
      } else {
        super.paintBackground(g);
      }
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    return new Dimension(size.width, Math.max(26, size.height));
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
      x+=middle.getIconWidth();
    }
    gg.dispose();
    right.paintIcon(c, g, stop, r.y);

    boolean withHistoryPopup = isSearchFieldWithHistoryPopup(c);
    Icon label = MacIntelliJIconCache.getIcon(withHistoryPopup ? "searchFieldWithHistory" : "searchFieldLabel");
    if (StringUtil.isEmpty(c.getText()) && !c.hasFocus() && !withHistoryPopup) {
      label.paintIcon(c, g, r.x + (r.width - label.getIconWidth())/ 2, r.y);
    } else {
      gg = g.create(0, 0, c.getWidth(), c.getHeight());
      int offset = withHistoryPopup ? 5 : 8;
      gg.setClip(r.x + offset, r.y, StringUtil.isEmpty(c.getText()) ? label.getIconWidth() : 16, label.getIconHeight());
      label.paintIcon(c, gg, r.x + offset, r.y);
    }

    if (!StringUtil.isEmpty(c.getText())) {
      Icon clear = MacIntelliJIconCache.getIcon("searchFieldClear");
      clear.paintIcon(c, g, r.x + r.width - clear.getIconWidth() - 6, r.y);
    }
  }

  @Override
  protected Rectangle getVisibleEditorRect() {
    Rectangle rect = super.getVisibleEditorRect();
    if (rect != null) {
      if (isSearchField(myTextField)) {
        rect.width -= 36;
        rect.x += 19;
        rect.y += 1;
      } else {
        rect.x += 2;
        rect.width -=4;
      }
    }
    return rect;
  }

  @Override
  protected void paintSafely(Graphics g) {
    paintBackground(g);
    super.paintSafely(g);
  }
}
