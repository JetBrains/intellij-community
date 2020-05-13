// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
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
    g.setPaint(bg);

    TableColumnModel model = ((JTableHeader)c).getColumnModel();

    final int h = c.getHeight();
    final int w = model.getTotalColumnWidth();
    g.fillRect(0, 0, w, h);
    JBColor bottomSeparatorColor = JBColor.namedColor("TableHeader.bottomSeparatorColor", ColorUtil.shift(bg, 0.75));
    g.setPaint(bottomSeparatorColor);
    LinePainter2D.paint((Graphics2D)g, 0, h - 1, w, h - 1);

    final Enumeration<TableColumn> columns = model.getColumns();

    final Color lineColor = JBColor.namedColor("TableHeader.separatorColor", ColorUtil.shift(bg, 0.7));
    int offset = 0;
    while (columns.hasMoreElements()) {
      final TableColumn column = columns.nextElement();
      if (columns.hasMoreElements() && column.getWidth() > 0) {
        offset += column.getWidth();
        g.setColor(lineColor);
        LinePainter2D.paint((Graphics2D)g, offset - 1, 1, offset - 1, h - 3);
      }
    }

    config.restore();

    super.paint(g, c);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    Dimension size = super.getPreferredSize(c);
    if (size.height == 0) return size;
    JBValue.UIInteger height = new JBValue.UIInteger("TableHeader.height", 25);
    return new Dimension(size.width, Math.max(height.get(), size.height));
  }
}
