/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.Patches;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ComponentWithEmptyText;
import com.intellij.util.ui.EmptyTextHelper;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.EventObject;

/**
 * The purpose of this class is to fix two Sun's bugs. There are #4503845 and #4330950.
 * When Sun fixes these bugs then the class should be immediately removed.
 * Also it adds "select row on first right click" functionality.
 *
 * @author Vladimir Kondratyev
 */
public class JBTable extends JTable implements ComponentWithEmptyText {
  private EmptyTextHelper myEmptyTextHelper;

  private MyCellEditorRemover myEditorRemover;

  public JBTable() {
    this(new DefaultTableModel());
  }

  public JBTable(final TableModel model) {
    super(model);
    myEmptyTextHelper = new EmptyTextHelper(this) {
      @Override
      protected boolean isEmpty() {
        return getRowCount() == 0;
      }
    };

    addMouseListener(new MyMouseListener());
    getColumnModel().addColumnModelListener(new TableColumnModelListener() {
      public void columnMarginChanged(ChangeEvent e) {
        if (cellEditor != null) {
          cellEditor.stopCellEditing();
        }
      }
      public void columnSelectionChanged(ListSelectionEvent e) {}
      public void columnAdded(TableColumnModelEvent e) {}
      public void columnMoved(TableColumnModelEvent e) {}
      public void columnRemoved(TableColumnModelEvent e) {}
    });
    getTableHeader().setDefaultRenderer(new MyTableHeaderRenderer());
    //noinspection UnusedDeclaration
    boolean marker = Patches.SUN_BUG_ID_4503845; // Don't remove. It's a marker for find usages
  }

  public static DefaultCellEditor createBooleanEditor() {
    return new DefaultCellEditor(new JCheckBox()) {
      {
        ((JCheckBox)getComponent()).setHorizontalAlignment(JCheckBox.CENTER);
      }
      @Override
      public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        Component component = super.getTableCellEditorComponent(table, value, isSelected, row, column);
        component.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return component;
      }
    };
  }

  public void resetDefaultFocusTraversalKeys() {
    KeyboardFocusManager m = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    for (Integer each : Arrays.asList(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS,
                                  KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS,
                                  KeyboardFocusManager.UP_CYCLE_TRAVERSAL_KEYS,
                                  KeyboardFocusManager.DOWN_CYCLE_TRAVERSAL_KEYS)) {
      setFocusTraversalKeys(each, m.getDefaultFocusTraversalKeys(each));
    }
  }

  public String getEmptyText() {
    return myEmptyTextHelper.getEmptyText();
  }

  public void setEmptyText(String emptyText) {
    myEmptyTextHelper.setEmptyText(emptyText);
  }

  public void setEmptyText(String emptyText, SimpleTextAttributes attrs) {
    myEmptyTextHelper.setEmptyText(emptyText, attrs);
  }

  public void clearEmptyText() {
    myEmptyTextHelper.clearEmptyText();
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs) {
    myEmptyTextHelper.appendEmptyText(text, attrs);
  }

  public void appendEmptyText(String text, SimpleTextAttributes attrs, ActionListener listener) {
    myEmptyTextHelper.appendEmptyText(text, attrs, listener);
  }

  private static class MyTableHeaderRenderer extends DefaultTableCellRenderer {
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      if (table != null) {
        JTableHeader header = table.getTableHeader();
        if (header != null) {
          setForeground(header.getForeground());
          setBackground(header.getBackground());
          setFont(header.getFont());
        }
        if (!table.isEnabled()) {
          setForeground(UIUtil.getTextInactiveTextColor());
        }
      }
      setText(value == null ? "" : value.toString());
      setBorder(UIUtil.getTableHeaderCellBorder());
      setHorizontalAlignment(JLabel.CENTER);

      return this;
    }
  }

  public void removeNotify() {
    final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    //noinspection HardCodedStringLiteral
    keyboardFocusManager.removePropertyChangeListener("permanentFocusOwner", myEditorRemover);
    //noinspection HardCodedStringLiteral
    keyboardFocusManager.removePropertyChangeListener("focusOwner", myEditorRemover);
    super.removeNotify();
  }

  public boolean editCellAt(final int row, final int column, final EventObject e) {
    if (cellEditor != null && !cellEditor.stopCellEditing()) {
      return false;
    }

    if (row < 0 || row >= getRowCount() || column < 0 || column >= getColumnCount()) {
      return false;
    }

    if (!isCellEditable(row, column)) {
      return false;
    }

    if (myEditorRemover == null) {
      final KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      myEditorRemover = new MyCellEditorRemover(keyboardFocusManager);
      //noinspection HardCodedStringLiteral
      keyboardFocusManager.addPropertyChangeListener("focusOwner", myEditorRemover);
      //noinspection HardCodedStringLiteral
      keyboardFocusManager.addPropertyChangeListener("permanentFocusOwner", myEditorRemover);
    }

    final TableCellEditor editor = getCellEditor(row, column);
    if (editor != null && editor.isCellEditable(e)) {
      editorComp = prepareEditor(editor, row, column);
      if (editorComp == null) {
        removeEditor();
        return false;
      }
      editorComp.setBounds(getCellRect(row, column, false));
      add(editorComp);
      editorComp.validate();

      editorComp.requestFocusInWindow();

      setCellEditor(editor);
      setEditingRow(row);
      setEditingColumn(column);
      editor.addCellEditorListener(this);

      return true;
    }
    return false;
  }

  private final class MyCellEditorRemover implements PropertyChangeListener {
    private final KeyboardFocusManager myFocusManager;

    public MyCellEditorRemover(final KeyboardFocusManager focusManager) {
      myFocusManager = focusManager;
    }

    public void propertyChange(final PropertyChangeEvent e) {
      if (!isEditing()) {
        return;
      }

      Component c = myFocusManager.getFocusOwner();
      while (c != null) {
        if (c == JBTable.this) {
          // focus remains inside the table
          return;
        }
        else if (c instanceof Window) {
          if (c == SwingUtilities.getWindowAncestor(JBTable.this)) {
            getCellEditor().stopCellEditing();
          }
          break;
        }
        c = c.getParent();
      }
    }
  }

  public void fixColumnWidthToHeader(final int columnIdx) {
    final TableColumn column = getColumnModel().getColumn(columnIdx);
    final int width = getTableHeader().getFontMetrics(getTableHeader().getFont()).stringWidth(getColumnName(columnIdx)) + 2;
    column.setMinWidth(width);
    column.setMaxWidth(width);
  }

  private final class MyMouseListener extends MouseAdapter {
    public void mousePressed(final MouseEvent e) {
      if (SwingUtilities.isRightMouseButton(e)) {
        final int[] selectedRows = getSelectedRows();
        if (selectedRows.length < 2) {
          final int row = rowAtPoint(e.getPoint());
          if (row != -1) {
            getSelectionModel().setSelectionInterval(row, row);
          }
        }
      }
    }
  }
}