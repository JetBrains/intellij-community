package com.intellij.tools;

import com.intellij.CommonBundle;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TableUtil;
import com.intellij.util.ui.ItemRemovable;
import com.intellij.util.ui.Table;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.Arrays;

class ToolsPanel extends JPanel {
  private final MyModel myModel;
  private final JTable myTable;
  private final JButton myAddButton = new JButton(ToolsBundle.message("tools.add.button"));
  private final JButton myCopyButton = new JButton(ToolsBundle.message("tools.copy.button"));
  private final JButton myEditButton = new JButton(ToolsBundle.message("tools.edit.button"));
  private final JButton myMoveUpButton = new JButton(ToolsBundle.message("tools.move.up.button"));
  private final JButton myMoveDownButton = new JButton(ToolsBundle.message("tools.move.down.button"));
  private final JButton myRemoveButton = new JButton(ToolsBundle.message("tools.remove.button"));

  ToolsPanel() {
    myModel = new MyModel();
    myTable = new Table(myModel);

    myTable.setShowGrid(false);
    myTable.setIntercellSpacing(new Dimension(0, 0));
    myTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
    myTable.setColumnSelectionAllowed(false);
    myTable.setPreferredScrollableViewportSize(new Dimension(400, 300));
    JScrollPane tableScrollPane = ScrollPaneFactory.createScrollPane(myTable);

    int width = new JCheckBox().getPreferredSize().width;
    TableColumnModel columnModel = myTable.getColumnModel();
    TableColumn column = columnModel.getColumn(0);
    column.setPreferredWidth(width);
    column.setMaxWidth(width);

    DefaultTableCellRenderer renderer = new MyTableCellRenderer();
    columnModel.getColumn(1).setCellRenderer(renderer);
    columnModel.getColumn(2).setCellRenderer(renderer);
    columnModel.getColumn(3).setCellRenderer(renderer);

    setLayout(new GridBagLayout());
    GridBagConstraints constr;

    // tools label
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.anchor = GridBagConstraints.WEST;
    constr.insets = new Insets(5, 5, 0, 0);
    add(new JLabel(ToolsBundle.message("tools.caption")), constr);

    // tools list
    constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 1;
    constr.weightx = 1;
    constr.weighty = 1;
    constr.insets = new Insets(0, 5, 0, 0);
    constr.fill = GridBagConstraints.BOTH;
    constr.anchor = GridBagConstraints.WEST;
    add(tableScrollPane, constr);
    myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    // right side buttons
    constr = new GridBagConstraints();
    constr.gridx = 1;
    constr.gridy = 1;
    constr.anchor = GridBagConstraints.NORTH;
    add(createRightButtonPane(), constr);

//    // for align
//    constr = new GridBagConstraints();
//    constr.gridx = 2;
//    constr.weightx = 1;
//    constr.fill = GridBagConstraints.HORIZONTAL;
//    add(new JPanel(), constr);

    addListeners();

  }

  // add/edit/remove buttons
  private JPanel createRightButtonPane() {
    JPanel pane = new JPanel(new GridBagLayout());
    GridBagConstraints constr = new GridBagConstraints();
    constr.insets = new Insets(0, 5, 5, 5);
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.weightx = 1.0;
    constr.gridy = 0;
    pane.add(myAddButton, constr);
    constr.gridy = 1;
    pane.add(myCopyButton, constr);
    constr.gridy = 2;
    pane.add(myEditButton, constr);
    constr.gridy = 3;
    pane.add(myRemoveButton, constr);
    constr.gridy = 4;
    pane.add(myMoveUpButton, constr);
    constr.gridy = 5;
    pane.add(myMoveDownButton, constr);
    return pane;
  }

  void reset() {
    Tool[] tools = ToolManager.getInstance().getTools();
    while (myModel.getRowCount() > 0) {
      myModel.removeRow(0);
    }
    for (int i = 0; i < tools.length; i++) {
      Tool tool = tools[i];
      Tool toolCopy = new Tool();
      toolCopy.copyFrom(tool);
      addRow(new ToolWrapper(toolCopy));
    }
    if (myModel.getRowCount() > 0) {
      myTable.setRowSelectionInterval(0, 0);
    }
    else {
      myTable.getSelectionModel().clearSelection();
    }
    update();
  }

  private void addRow(ToolWrapper toolWrapper) {
    myModel.addRow(new Object[]{toolWrapper.getTool().isEnabled() ? Boolean.TRUE : Boolean.FALSE, toolWrapper});
  }

  void apply() throws IOException{
    // unregister removed tools
    ToolManager toolManager = ToolManager.getInstance();

    for (int i = 0; i < myModel.getRowCount(); i++) {
      ToolWrapper wrapper = myModel.getToolWrapper(i);
      wrapper.commit();
    }
    toolManager.setTools(getTools());
    toolManager.writeTools();
  }

  boolean isModified() {
    Tool[] tools = new Tool[myModel.getRowCount()];
    for (int i = 0; i < myModel.getRowCount(); i++) {
      ToolWrapper wrapper = myModel.getToolWrapper(i);
      tools[i] = wrapper.getTool();
    }
    return !Arrays.equals(ToolManager.getInstance().getTools(), tools);
  }

  /**
   * Tool info shown in list
   */
  private static class ToolWrapper {
    private Tool myTool;

    public ToolWrapper(Tool tool) {
      myTool = tool;
    }

    public String toString() {
      return myTool.getName();
    }

    public Tool getTool() {
      return myTool;
    }

    public void commit() {
      Tool newTool = new Tool();
      newTool.copyFrom(myTool);
      myTool = newTool;
    }
  }

  private void addListeners() {
    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        update();
      }
    });

    myTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && myTable.columnAtPoint(e.getPoint()) != 0) {
          editSelected();
        }
      }
    });

    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        ToolEditorDialog dlg = new ToolEditorDialog(ToolsPanel.this);
        Tool tool = new Tool();
        tool.setUseConsole(true);
        tool.setFilesSynchronizedAfterRun(true);
        tool.setShownInMainMenu(true);
        tool.setShownInEditor(true);
        tool.setShownInProjectViews(true);
        tool.setShownInSearchResultsPopup(true);
        tool.setEnabled(true);
        dlg.setData(tool, ToolManager.getInstance().getGroups(getTools()));
        dlg.show();
        if (dlg.isOK()) {
          addRow(new ToolWrapper(dlg.getData()));
          int lastIndex = myModel.getRowCount()-1;
          myTable.setRowSelectionInterval(lastIndex, lastIndex);
        }
        myTable.requestFocus();
      }
    });

    myCopyButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          int index = myTable.getSelectionModel().getMinSelectionIndex();
          if (index == -1 || myTable.getSelectionModel().getMaxSelectionIndex() != index) return;

          ToolWrapper toolWrapper = myModel.getToolWrapper(index);
          Tool originalTool = toolWrapper.getTool();

          ToolEditorDialog dlg = new ToolEditorDialog(ToolsPanel.this);
          Tool toolCopy = new Tool();
          toolCopy.copyFrom(originalTool);
          dlg.setData(toolCopy, ToolManager.getInstance().getGroups(getTools()));
          dlg.show();
          if (dlg.isOK()) {
            addRow(new ToolWrapper(dlg.getData()));
            int lastIndex = myModel.getRowCount()-1;
            myTable.getSelectionModel().setSelectionInterval(lastIndex, lastIndex);
          }
          myTable.requestFocus();
        }
      }
    );

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        editSelected();
        myTable.requestFocus();
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        removeSelected();
      }
    });

    myMoveUpButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModel.setSynchronize(false);
        TableUtil.moveSelectedItemsUp(myTable);
        myModel.setSynchronize(true);
        myTable.requestFocus();
      }
    });

    myMoveDownButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        myModel.setSynchronize(false);
        TableUtil.moveSelectedItemsDown(myTable);
        myModel.setSynchronize(true);
        myTable.requestFocus();
      }
    });


    InputMap inputMap = myTable.getInputMap();
    @NonNls Object o = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0));
    if (o == null) {
      o = "enable_disable";
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), o);
    }
    myTable.getActionMap().put(o, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (myTable.isEditing()) return;
        ListSelectionModel selectionModel = myTable.getSelectionModel();
        for (int i = 0; i < myModel.getRowCount(); i++) {
          if (selectionModel.isSelectedIndex(i)) {
            Boolean aValue = (Boolean)myModel.getValueAt(i, 0);
            myModel.setValueAt(aValue.booleanValue() ? Boolean.FALSE : Boolean.TRUE, i, 0);
          }
        }
      }
    });

    o = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    if (o == null) {
      o = "edit_selected";
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), o);
    }
    myTable.getActionMap().put(o, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        editSelected();
      }
    });

    o = inputMap.get(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
    if (o == null) {
      o = "remove_selected";
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), o);
    }
    myTable.getActionMap().put(o, new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        removeSelected();
      }
    });
  }

  private void update() {
    int minSelectionIndex = myTable.getSelectionModel().getMinSelectionIndex();
    int maxSelectionIndex = myTable.getSelectionModel().getMaxSelectionIndex();

    // enable buttons
    myRemoveButton.setEnabled(minSelectionIndex != -1);
    myMoveUpButton.setEnabled(minSelectionIndex > 0);
    myMoveDownButton.setEnabled(maxSelectionIndex != -1 && maxSelectionIndex < myModel.getRowCount()-1);
    boolean selectedOne = minSelectionIndex != -1 && minSelectionIndex == maxSelectionIndex;
    myCopyButton.setEnabled(selectedOne);
    myEditButton.setEnabled(selectedOne);

    myTable.repaint();
  }

  private Tool[] getTools() {
    Tool[] tools = new Tool[myModel.getRowCount()];
    for (int i = 0; i < tools.length; i++) {
      tools[i] = myModel.getToolWrapper(i).getTool();
    }
    return tools;
  }

  private void removeSelected() {
    int result = Messages.showYesNoDialog(
      this,
      ToolsBundle.message("tools.delete.confirmation"),
      CommonBundle.getWarningTitle(),
      Messages.getWarningIcon()
    );
    if (result != 0) {
      return;
    }
    TableUtil.removeSelectedItems(myTable);
    update();
    myTable.requestFocus();
  }

  private void editSelected() {
    int index = myTable.getSelectionModel().getMinSelectionIndex();
    if (index == -1 || index != myTable.getSelectionModel().getMaxSelectionIndex()) return;
    ToolWrapper toolWrapper = myModel.getToolWrapper(index);
    if (toolWrapper != null) {
      ToolEditorDialog dlg = new ToolEditorDialog(this);
      dlg.setData(toolWrapper.getTool(), ToolManager.getInstance().getGroups(getTools()));
      dlg.show();
      if (dlg.isOK()) {
        toolWrapper.getTool().copyFrom(dlg.getData());
        update();
      }
    }
  }

  class MyModel extends DefaultTableModel implements ItemRemovable {
    private boolean myDoSynchronize = true;

    public MyModel() {
      super(new Object[0][], new Object[]{" ",
        ToolsBundle.message("tools.name.column"),
        ToolsBundle.message("tools.group.column"),
        ToolsBundle.message("tools.description.column")
      });
    }

    public Class getColumnClass(int columnIndex) {
      if (columnIndex == 0) return Boolean.class;
      return super.getColumnClass(columnIndex);
    }

    public boolean isCellEditable(int row, int column) {
      return column == 0;
    }

    public void setValueAt(Object aValue, int row, int column) {
      if (myDoSynchronize) {
        if (column == 0) {
          getToolWrapper(row).getTool().setEnabled(((Boolean)aValue).booleanValue());
        }
      }
      super.setValueAt(aValue, row, column);
      myTable.repaint();
    }

    public Object getValueAt(int row, int column) {
      switch (column) {
        case 2:
          return getToolWrapper(row).getTool().getGroup();
        case 3:
          return getToolWrapper(row).getTool().getDescription();
        default:
          return super.getValueAt(row, column);
      }
    }

    public ToolWrapper getToolWrapper(int row) {
      return (ToolWrapper)getValueAt(row, 1);
    }

    public void setSynchronize(boolean flag) {
      myDoSynchronize = flag;
    }
  }

  private final class MyTableCellRenderer extends DefaultTableCellRenderer{
    public Component getTableCellRendererComponent(
      JTable table,
      Object value,
      boolean isSelected,
      boolean hasFocus,
      int row,
      int column
    ) {
      super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
      setEnabled(myModel.getToolWrapper(row).getTool().isEnabled());
      return this;
    }
  }
}