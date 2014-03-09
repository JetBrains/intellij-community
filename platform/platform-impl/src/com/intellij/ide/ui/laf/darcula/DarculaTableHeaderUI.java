/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.Enumeration;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaTableHeaderUI extends BasicTableHeaderUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaTableHeaderUI();
  }

  @Override
  public void paint(Graphics g2, JComponent c) {
    final Graphics2D g = (Graphics2D)g2;
    final GraphicsConfig config = new GraphicsConfig(g);
    final Color bg = c.getBackground();
    g.setPaint(new GradientPaint(0, 0, ColorUtil.shift(bg, 1.4), 0, c.getHeight(), ColorUtil.shift(bg, 0.9)));
    final int h = c.getHeight();
    final int w = c.getWidth();
    g.fillRect(0,0, w, h);
    g.setPaint(ColorUtil.shift(bg, 0.75));
    g.drawLine(0, h-1, w, h-1);
    g.drawLine(w-1, 0, w-1, h-1);

    final Enumeration<TableColumn> columns = ((JTableHeader)c).getColumnModel().getColumns();

    final Color lineColor = ColorUtil.shift(bg, 0.7);
    final Color shadow = Gray._255.withAlpha(30);
    int offset = 0;
    while (columns.hasMoreElements()) {
      final TableColumn column = columns.nextElement();
      if (columns.hasMoreElements() && column.getWidth() > 0) {
        offset += column.getWidth();
        g.setColor(lineColor);
        g.drawLine(offset-1, 1, offset-1, h-3);
        g.setColor(shadow);
        g.drawLine(offset, 1, offset, h-3);
      }
    }

    config.restore();

    super.paint(g, c);
  }
}
