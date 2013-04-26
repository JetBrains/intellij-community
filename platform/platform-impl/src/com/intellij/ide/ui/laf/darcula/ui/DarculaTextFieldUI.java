/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.DimensionUIResource;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTextFieldUI extends BasicTextFieldUI implements FocusListener {
  private static final Icon SEARCH_ICON = IconLoader.findIcon("/com/intellij/ide/ui/laf/darcula/icons/search.png", DarculaTextFieldUI.class, true);
  private static final Icon SEARCH_WITH_HISTORY_ICON = IconLoader.findIcon("/com/intellij/ide/ui/laf/darcula/icons/searchWithHistory.png", DarculaTextFieldUI.class, true);
  private static final Icon CLEAR_ICON = IconLoader.findIcon("/com/intellij/ide/ui/laf/darcula/icons/clear.png", DarculaTextFieldUI.class, true);

  private final JTextField myTextField;
  protected JLabel myClearIcon;
  protected JLabel myRecentIcon;

  public DarculaTextFieldUI(JTextField textField) {
    myTextField = textField;
    myTextField.addFocusListener(this);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    c.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        c.repaint();
      }

      @Override
      public void focusLost(FocusEvent e) {
        c.repaint();
      }
    });
    return new DarculaTextFieldUI((JTextField)c);
  }

  @Override
  protected void paintSafely(Graphics g) {
    super.paintSafely(g);
  }

  @Override
  protected void paintBackground(Graphics graphics) {
    Graphics2D g = (Graphics2D)graphics;
    final JTextComponent c = getComponent();
    final Container parent = c.getParent();
    if (parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

    final Border border = c.getBorder();
    if (isSearchField(c)) {
      g.setColor(c.getBackground());
      final JBInsets i = JBInsets.create(c.getInsets());
      int x = i.right - 4 - 16;
      int y = i.top - 3;
      int w = c.getWidth() - i.width() + 16*2 +7*2  - 5;
      int h = c.getBounds().height - i.height() + 4*2 - 3;
      if (h%2==1) h++;
      int r = h;
      g.fillRoundRect(x, y, w, h, r, r);
      g.setColor(c.isEnabled() ? Gray._100 : new Color(0x535353));
      if (c.hasFocus()) {
        DarculaUIUtil.paintSearchFocusRing(g, new Rectangle(x, y, w, h));
      } else {
        g.drawRoundRect(x, y, w, h, r, r);
      }
      SEARCH_ICON.paintIcon(null, g, x + 3, y + (h-16)/2 + 1);
      if (getComponent().getText().length() > 0) {
        CLEAR_ICON.paintIcon(null, g, x + w - 16 - 1, y + (h-16)/2);
      }
    } else if (border instanceof DarculaTextBorder) {
      if (c.isEnabled() && c.isEditable()) {
        g.setColor(c.getBackground());
      }
      final int width = c.getWidth();
      final int height = c.getHeight();
      final Insets i = border.getBorderInsets(c);
      if (c.hasFocus()) {
        g.fillRoundRect(i.left - 5, i.top - 2, width - i.right - i.left + 10, height - i.top - i.bottom + 6, 5, 5);
      } else {
        g.fillRect(i.left - 5, i.top - 2, width - i.right - i.left + 12, height - i.top - i.bottom + 6);
      }
    } else {
      super.paintBackground(g);
    }
    config.restore();
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    final Dimension size = super.getMinimumSize(c);
    return new DimensionUIResource(size.width, 24);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    final Dimension size = super.getPreferredSize(c);
    return new DimensionUIResource(size.width, 24);
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    final Dimension size = super.getMaximumSize(c);
    return new DimensionUIResource(size.width, 24);
  }

  public static boolean isSearchField(Component c) {
    return c instanceof JTextField && "search".equals(((JTextField)c).getClientProperty("JTextField.variant"));
  }

  @Override
  public void focusGained(FocusEvent e) {
    myTextField.repaint();
  }

  @Override
  public void focusLost(FocusEvent e) {
    myTextField.repaint();
  }
}
