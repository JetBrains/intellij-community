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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.Gray;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.text.JTextComponent;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextFieldUI extends TextFieldWithPopupHandlerUI {


  public DarculaTextFieldUI(JTextField textField) {
    super(textField);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaTextFieldUI((JTextField)c);
  }

  public void showSearchPopup() {
    final Object value = myTextField.getClientProperty("JTextField.Search.FindPopup");
    final JTextComponent editor = getComponent();
    if (editor != null && value instanceof JPopupMenu) {
      final JPopupMenu popup = (JPopupMenu)value;
      popup.show(editor, getSearchIconCoord().x, editor.getHeight());
    }
  }

  public SearchAction getActionUnder(@NotNull Point p) {
    int off = JBUI.scale(8);
    Point point = new Point(p.x - off, p.y - off);
    return point.distance(getSearchIconCoord()) <= off
           ? SearchAction.POPUP
           : hasText() && point.distance(getClearIconCoord()) <= off
             ? SearchAction.CLEAR
             : null;
  }

  protected Rectangle getDrawingRect() {
    final JTextComponent c = myTextField;
    final JBInsets i = JBInsets.create(c.getInsets());
    final int x = i.right - JBUI.scale(4) - JBUI.scale(16);
    final int y = i.top - JBUI.scale(3);
    final int w = c.getWidth() - i.width() + JBUI.scale(16*2 +7*2  - 5);
    int h = c.getBounds().height - i.height() + JBUI.scale(4*2 - 3);
    if (h%2==1) h++;
    return new Rectangle(x, y, w, h);
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
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

    final Border border = c.getBorder();
    if (isSearchField(c)) {
      paintSearchField(g, c, r);
    } else if (border instanceof DarculaTextBorder) {
      paintDarculaBackground(g, c, border);
    } else {
      super.paintBackground(g);
    }
    config.restore();
  }

  protected void paintDarculaBackground(Graphics2D g, JTextComponent c, Border border) {
    if (c.isEnabled() && c.isEditable()) {
      g.setColor(c.getBackground());
    }
    final int width = c.getWidth();
    final int height = c.getHeight();
    final Insets i = border.getBorderInsets(c);
    if (!icons.isEmpty()) {
      for (IconHolder holder : icons.values()) {
        int space = holder.bounds.width + holder.extension.getIconGap();
        if (holder.extension.isIconBeforeText()) {
          i.left -= space;
        }
        else {
          i.right -= space;
        }
      }
    }
    if (c.hasFocus()) {
      g.fillRoundRect(i.left - JBUI.scale(5), i.top - JBUI.scale(2), width - i.right - i.left + JBUI.scale(10), height - i.top - i.bottom + JBUI.scale(6), JBUI.scale(5), JBUI.scale(5));
    } else {
      g.fillRect(i.left - JBUI.scale(5), i.top - JBUI.scale(2), width - i.right - i.left + JBUI.scale(10), height - i.top - i.bottom + JBUI.scale(6));
    }
  }

  protected void paintSearchField(Graphics2D g, JTextComponent c, Rectangle r) {
    final boolean noBorder = c.getClientProperty("JTextField.Search.noBorderRing") == Boolean.TRUE;
    int radius = r.height-1;
    if (noBorder) {
      g.setColor(c.getBackground());
      RectanglePainter.FILL.paint(g, r.x, r.y, r.width, r.height, radius);
    }
    else if (c.hasFocus()) {
      g.setColor(c.getBackground());
      RectanglePainter.FILL.paint(g, r.x, r.y, r.width, r.height, radius);
      DarculaUIUtil.paintSearchFocusRing(g, r, c);
    }
    else {
      RectanglePainter.paint(g, r.x, r.y, r.width, r.height, radius, c.getBackground(), c.isEnabled() ? Gray._100 : Gray._83);
    }
    Point p = getSearchIconCoord();
    Icon searchIcon = myTextField.getClientProperty("JTextField.Search.FindPopup") instanceof JPopupMenu ? UIManager.getIcon("TextField.darcula.searchWithHistory.icon") : UIManager.getIcon("TextField.darcula.search.icon");
    if (searchIcon == null) {
      searchIcon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/search.png", DarculaTextFieldUI.class, true);
    }
    searchIcon.paintIcon(null, g, p.x, p.y);
    if (hasText()) {
      p = getClearIconCoord();
      Icon clearIcon = UIManager.getIcon("TextField.darcula.clear.icon");
      if (clearIcon == null) {
        clearIcon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/clear.png", DarculaTextFieldUI.class, true);
      }
      clearIcon.paintIcon(null, g, p.x, p.y);
    }
  }
}
