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
package com.intellij.openapi.diff.impl.dir;

import com.intellij.ide.diff.DirDiffOperation;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffTableCellRenderer extends DefaultTableCellRenderer {
  @Override
  public Component getTableCellRendererComponent(final JTable table, Object value, boolean isSelected, boolean hasFocus, final int row, final int column) {
    final DirDiffTableModel model = (DirDiffTableModel)table.getModel();
    final DirDiffElementImpl element = model.getElementAt(row);
    if (element == null) return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    final int modelColumn = table.convertColumnIndexToModel(column);

    if (element.isSeparator()) {
      return new SimpleColoredComponent() {
        {
          setIcon(ObjectUtils.chooseNotNull(element.getSourceIcon(), element.getTargetIcon()));
          append(element.getName());
        }
        @Override
        protected void doPaint(Graphics2D g) {
          int offset = 0;
          int i = 0;
          final TableColumnModel columnModel = table.getColumnModel();
          while (i < column) {
            offset += columnModel.getColumn(i).getWidth();
            i++;
          }
          g.translate(-offset, 0);
          super.doPaint(g);
          g.translate(offset, 0);
        }
      };
    }
    final Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    if (c instanceof JLabel) {
      final JLabel label = (JLabel)c;
      Border border = label.getBorder();
      if ((hasFocus || isSelected) && border != null) {
        label.setBorder(new EmptyBorder(border.getBorderInsets(label)));
      }
      label.setIcon(null);

      final DirDiffOperation op = element.getOperation();
      if (modelColumn == (table.getColumnCount() - 1) / 2) {
        label.setIcon(op.getIcon());
        label.setHorizontalAlignment(CENTER);
        return label;
      }

      Color fg = isSelected ? UIUtil.getTableSelectionForeground() : op.getTextColor();
      label.setForeground(fg);
      final String name = table.getColumnName(column);
      if (DirDiffTableModel.COLUMN_DATE.equals(name)) {
        label.setHorizontalAlignment(CENTER);
      } else if (DirDiffTableModel.COLUMN_SIZE.equals(name)) {
        label.setHorizontalAlignment(RIGHT);
      } else {
        label.setHorizontalAlignment(LEFT);
        final String text = label.getText();
        label.setText("  " + text);
        if (text != null && text.trim().length() > 0) {
          label.setIcon(modelColumn == 0 ? element.getSourceIcon() : element.getTargetIcon());
        }
      }
    }
    return c;
  }
}
