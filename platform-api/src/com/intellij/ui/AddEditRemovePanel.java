package com.intellij.ui;

import com.intellij.util.ui.Table;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author mike
 */
public abstract class AddEditRemovePanel<T> extends PanelWithButtons {
  private JTable myTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private TableModel myModel;
  private List<T> myData;
  private AbstractTableModel myTableModel;

  public AddEditRemovePanel(TableModel<T> model, List<T> data) {
    this(model, data, null);
  }

  public AddEditRemovePanel(TableModel<T> model, List<T> data, String labelText) {
    myModel = model;
    myData = data;

    initPanel();
    updateButtons();

    if (labelText != null) {
      setBorder(BorderFactory.createTitledBorder(new EtchedBorder(),labelText));
    }
  }

  protected String getLabelText(){
    return null;
  }

  protected JComponent createMainComponent(){
    myTableModel = new AbstractTableModel() {
      public int getRowCount(){
        return myData != null ? myData.size() : 0;
      }

      public int getColumnCount(){
        return myModel.getColumnCount();
      }

      public String getColumnName(int column){
        return myModel.getColumnName(column);
      }

      public Class getColumnClass(int columnIndex){
        return String.class;
      }

      public Object getValueAt(int rowIndex, int columnIndex){
        return myModel.getField(myData.get(rowIndex), columnIndex);
      }
    };

    myTable = new Table(myTableModel);
    myTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myTable.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e){
        if (e.getClickCount() == 2) doEdit();
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e){
        updateButtons();
      }
    });

    return ScrollPaneFactory.createScrollPane(myTable);
  }

  protected JButton[] createButtons(){
    myAddButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e){
          doAdd();
        }
      }
    );

    myEditButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e){
          doEdit();
        }
      }
    );

    myRemoveButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e){
          doRemove();
        }
      }
    );

    return new JButton[]{myAddButton, myEditButton, myRemoveButton};
  }

  protected void doAdd() {
    T o = addItem();
    if (o == null) return;

    myData.add(o);
    int index = myData.size() - 1;
    myTableModel.fireTableRowsInserted(index, index);
    myTable.setRowSelectionInterval(index, index);
  }

  protected abstract T addItem();
  protected abstract boolean removeItem(T o);
  protected abstract T editItem(T o);

  protected void doEdit() {
    int selected = myTable.getSelectedRow();
    T o = editItem(myData.get(selected));
    if (o != null) myData.set(selected, o);

    myTableModel.fireTableRowsUpdated(selected, selected);
  }
  
  protected void doRemove() {
    final int[] selected = myTable.getSelectedRows();
    if (selected == null || selected.length == 0) return;
    for (int i = selected.length - 1; i >= 0; i--) {
      int idx = selected[i];
      if (!removeItem(myData.get(idx))) return;
      myData.remove(idx);
    }

    for (int i = selected.length - 1; i >= 0; i--) {
      int idx = selected[i];
      myTableModel.fireTableRowsDeleted(idx, idx);
    }
    int selection = selected[0];
    if (selection >= myData.size()) {
      selection = myData.size() - 1;
    }
    if (selection >= 0) {
      myTable.setRowSelectionInterval(selection, selection);
    }   
  }

  public void setData(java.util.List<T> data) {
    myData = data;
    myTableModel.fireTableDataChanged();
  }

  public List<T> getData() {
    return myData;
  }

  public void setRenderer(int index, TableCellRenderer renderer) {
      myTable.getColumn(myModel.getColumnName(index)).setCellRenderer(renderer);
  }

  private void updateButtons() {
    myEditButton.setEnabled(myTable.getSelectedRowCount() == 1);
    myRemoveButton.setEnabled(myTable.getSelectedRowCount() >= 1);
  }

  public void setSelected(Object o) {
    for(int i = 0; i < myTableModel.getRowCount(); ++i) {
      if (myData.get(i).equals(o)) {
        myTable.getSelectionModel().setSelectionInterval(i,i);
        break;
      }
    }
  }
  
  public static interface TableModel<T> {
    int getColumnCount();
    String getColumnName(int columnIndex);
    Object getField(T o, int columnIndex);
   }
}
