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
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaComboBoxUI extends BasicComboBoxUI implements Border {
  private final JComboBox myComboBox;
  private JBInsets myComboBoxInsets = new JBInsets(4, 7, 4, 3);

  public DarculaComboBoxUI(JComboBox comboBox) {
    myComboBox = comboBox;
    myComboBox.setBorder(this);
  }

  public static ComponentUI createUI(final JComponent c) {
    return new DarculaComboBoxUI(((JComboBox)c));
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
        g.setColor(myComboBox.isEditable() ? UIUtil.getControlColor() : UIUtil.getControlColor());
        g.fillRect(0, 0, w, h);
        g.setColor(getForeground());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
        g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
        final int xU = w / 4;
        final int yU = h / 4;
        final Path2D.Double path = new Path2D.Double();
        path.moveTo(xU+1, yU + 2);
        path.lineTo(3 * xU + 1, yU + 2);
        path.lineTo(2*xU+1, 3*yU );
        path.lineTo(xU+1, yU + 2);
        path.closePath();
        g.fill(path);
        g.setColor(ColorUtil.fromHex("939393"));
        g.drawLine(0, 0, 0 , h);
        //paintTriangle(g, w / 2, h / 2, 5, SOUTH, myComboBox.isEnabled());
        //g.setColor(ColorUtil.fromHex("939393"));
        //g.drawLine(0,0, 0,h);
        config.restore();
      }
    };
    button.setBorder(BorderFactory.createEmptyBorder());
    button.setOpaque(false);
    return button;
  }

  @Override
  protected Insets getInsets() {
    return myComboBoxInsets;
  }

  @Override
  public void paintBorder(Component c, Graphics g2, int x, int y, int width, int height) {
    final Graphics2D g = (Graphics2D)g2;
    if (hasFocus || (editor != null && editor.hasFocus())) {
      DarculaUIUtil.paintFocusRing(g, 2, 2, width - 4, height - 4);
    } else {
      g.setColor(ColorUtil.fromHex("939393"));
      final GraphicsConfig config = new GraphicsConfig(g);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
      g.drawRoundRect(1, 1, width - 2, height - 2, 5,5);
      config.restore();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return myComboBoxInsets;
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }
}