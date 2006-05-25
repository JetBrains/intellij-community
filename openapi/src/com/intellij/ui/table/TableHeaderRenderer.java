/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ui.table;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.SortableColumnModel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * author: lesya
 */
public class TableHeaderRenderer extends JPanel implements TableCellRenderer{
  private final SortableColumnModel myModel;
  protected final JLabel myLabel = new JLabel("", JLabel.CENTER);
  private final JPanel myIconPanel = new JPanel(new BorderLayout());
  private final JPanel mySpace = new JPanel(new BorderLayout());
  private final JLabel myIconLabel = new JLabel();
  private final Border myBorder;

  public TableHeaderRenderer(SortableColumnModel model) {
    this(model, UIUtil.getTableHeaderCellBorder());
  }
  public TableHeaderRenderer(SortableColumnModel model, Border border) {
    super(new BorderLayout(0, 0));
    myModel = model;

    add(myLabel, BorderLayout.CENTER);
    add(myIconPanel, BorderLayout.EAST);

    mySpace.setMaximumSize(new Dimension(10, 0));
    mySpace.setOpaque(false);

    myIconPanel.add(myIconLabel, BorderLayout.CENTER);
    myIconPanel.add(mySpace, BorderLayout.EAST);
    myIconPanel.setOpaque(false);
    myBorder = border;
  }

  public Component getTableCellRendererComponent(JTable table,
                                                 Object value,
                                                 boolean isSelected,
                                                 boolean hasFocus,
                                                 int row,
                                                 int column) {
    Icon icon = null;

    int logicalIndex = table.convertColumnIndexToModel(column);
    if ((logicalIndex) >= myModel.getColumnInfos().length) {
      setText("");
      return this;
    }
    final ColumnInfo columnInfo = myModel.getColumnInfos()[logicalIndex];
    String labelString = columnInfo.getName();
    if (myModel.isSortable() && columnInfo.isSortable() && myModel.getSortedColumnIndex() == logicalIndex) {
      //noinspection HardCodedStringLiteral
      labelString = "<html><b>" + labelString + "</b></html>";
      if (myModel.getSortingType() == SortableColumnModel.SORT_ASCENDING) {
        icon = IconLoader.getIcon("/actions/sortAsc.png");
      }
      if (myModel.getSortingType() == SortableColumnModel.SORT_DESCENDING) {
        icon = IconLoader.getIcon("/actions/sortDesc.png");
      }
    }

    setText(labelString);
    setIcon(icon);


    JTableHeader header = table.getTableHeader();
    setForeground(header.getForeground());
    setBackground(header.getBackground());
     myLabel.setFont(header.getFont());

    setBorder(myBorder);

    return this;
  }

  private void setText(String labelString) {
    myLabel.setText(labelString);
  }

  private void setIcon(Icon icon) {
    myIconLabel.setIcon(icon);
    if (icon == null){
      myIconLabel.setMaximumSize(new Dimension(0, 0));
      mySpace.setMaximumSize(new Dimension(0, 0));
    } else {
      myIconLabel.setMaximumSize(myIconLabel.getPreferredSize());
      mySpace.setMaximumSize(new Dimension(10, 0));
    }
  }
}
