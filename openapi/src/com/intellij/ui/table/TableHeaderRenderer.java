package com.intellij.ui.table;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.SortableColumnModel;

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
  private final JLabel myLabel = new JLabel("", JLabel.CENTER);
  private final JPanel myIconPanel = new JPanel(new BorderLayout());
  private final JPanel mySpace = new JPanel();
  private final JLabel myIconLabel = new JLabel();
  private final Border myBorder;

  public TableHeaderRenderer(SortableColumnModel model) {
    this(model, UIManager.getBorder("TableHeader.cellBorder"));
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
    String name = myModel.getColumnInfos()[logicalIndex].getName();
    String labelString = name;
    if (myModel.isSortable() && myModel.getSortedColumnIndex() == logicalIndex) {
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
