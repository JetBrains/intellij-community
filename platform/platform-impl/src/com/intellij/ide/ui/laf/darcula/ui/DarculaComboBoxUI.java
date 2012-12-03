/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("GtkPreferredJComboBoxRenderer")
public class DarculaComboBoxUI extends BasicComboBoxUI implements Border {
  private final JComboBox myComboBox;

  public DarculaComboBoxUI(JComboBox comboBox) {
    super();
    myComboBox = comboBox;
    myComboBox.setBorder(this);
  }

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaComboBoxUI(((JComboBox)c));
  }

  @Override
  protected ListCellRenderer createRenderer() {
    return new BasicComboBoxRenderer.UIResource() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (c instanceof JComponent) {
          final JComponent jc = (JComponent)c;
          if (index == -1) {
            jc.setOpaque(false);
            jc.setForeground(list.getForeground());
          } else {
            jc.setOpaque(true);
          }
        }
        return c;
      }
    };
  }

  protected JButton createArrowButton() {
    final Color bg = myComboBox.getBackground();
    final Color fg = myComboBox.getForeground();
    JButton button = new BasicArrowButton(SwingConstants.SOUTH, bg, fg, fg, fg) {

      @Override
      public void paint(Graphics g2) {
        final Graphics2D g = (Graphics2D)g2;
        final GraphicsConfig config = new GraphicsConfig(g);

        final int w = getWidth();
        final int h = getHeight();
        g.setColor(UIUtil.getControlColor());
        g.fillRect(0, 0, w, h);
        g.setColor(myComboBox.isEnabled() ? getForeground() : getForeground().darker());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        final int xU = w / 4;
        final int yU = h / 4;
        final Path2D.Double path = new Path2D.Double();
        g.translate(1, 1);
        path.moveTo(xU + 1, yU + 2);
        path.lineTo(3 * xU + 1, yU + 2);
        path.lineTo(2 * xU + 1, 3 * yU);
        path.lineTo(xU + 1, yU + 2);
        path.closePath();
        g.fill(path);
        g.translate(-1, -1);
        g.setColor(getBorderColor());
        g.drawLine(0, -1, 0, h);
        config.restore();
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  @Override
  protected Insets getInsets() {
    return new InsetsUIResource(4, 7, 4, 5);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    final Container parent = c.getParent();
    if (parent != null) {
      g.setColor(parent.getBackground());
      g.fillRect(0, 0, c.getWidth(), c.getHeight());
    }
    paintBorder(c, g, 0, 0, c.getWidth(), c.getHeight());
    hasFocus = comboBox.hasFocus();
    Rectangle r = rectangleForCurrentValue();
    paintCurrentValueBackground(g, r, hasFocus);
    paintCurrentValue(g, r, hasFocus);
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
  }

  @Override
  public void paintBorder(Component c, Graphics g2, int x, int y, int width, int height) {
    if (comboBox == null || arrowButton == null) {
      return; //NPE on LaF change
    }

    hasFocus = false;
    checkFocus();
    final Graphics2D g = (Graphics2D)g2;
    final Rectangle arrowButtonBounds = arrowButton.getBounds();
    final int xxx = arrowButtonBounds.x - 5;
    final GraphicsConfig config = new GraphicsConfig(g);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
    if (editor != null && comboBox.isEditable()) {
      ((JComponent)editor).setBorder(null);
      g.setColor(editor.getBackground());
      g.fillRoundRect(x + 1, y + 1, width - 2, height - 4, 5, 5);
      g.setColor(arrowButton.getBackground());
      g.fillRoundRect(xxx, y + 1, width - xxx, height - 4, 5, 5);
      g.setColor(editor.getBackground());
      g.fillRect(xxx, y + 1, 5, height - 4);
    } else {
      g.setColor(UIUtil.getPanelBackground());
      g.fillRoundRect(x + 1, y + 1, width - 2, height - 4, 5, 5);
    }

    final Color borderColor = getBorderColor();//ColorUtil.shift(UIUtil.getBorderColor(), 4);
    g.setColor(borderColor);
    int off = hasFocus ? 1 : 0;
    g.drawLine(xxx + 5, y + 1 + off, xxx + 5, height - 3);

    Rectangle r = rectangleForCurrentValue();
    paintCurrentValueBackground(g,r,hasFocus);
    paintCurrentValue(g,r,hasFocus);

    if (hasFocus) {
      DarculaUIUtil.paintFocusRing(g, 2, 2, width - 4, height - 4);
    }
    else {
      g.setColor(borderColor);
      g.drawRoundRect(1, 1, width - 2, height - 4, 5, 5);
    }
    config.restore();
  }

  private void checkFocus() {
    hasFocus = hasFocus(comboBox);
    if (hasFocus) return;

    final ComboBoxEditor ed = comboBox.getEditor();
    editor = ed == null ? null : ed.getEditorComponent();
    if (editor != null) {
      hasFocus = hasFocus(editor);
    }
  }

  private static boolean hasFocus(Component c) {
    final Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    return owner != null && SwingUtilities.isDescendingFrom(owner, c);
  }

  private static Gray getBorderColor() {
    return Gray._100;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new InsetsUIResource(4, 7, 4, 5);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}