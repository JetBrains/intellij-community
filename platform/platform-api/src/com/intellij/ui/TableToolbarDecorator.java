/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.actionSystem.ActionToolbarPosition;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public class TableToolbarDecorator {
  private JTable myTable;
  private TableModel myModel;
  private Border myToolbarBorder;
  private boolean myAddActionEnabled;
  private boolean myRemoveActionEnabled;
  private boolean myUpActionEnabled;
  private boolean myDownActionEnabled;
  private Border myBorder;
  private List<AnActionButton> myExtraActions = new ArrayList<AnActionButton>();
  private ActionToolbarPosition myToolbarPosition;
  private Runnable myAddAction;
  private Runnable myRemoveAction;
  private Runnable myUpAction;
  private Runnable myDownAction;
  private AddRemoveUpDownPanel myPanel;


  private TableToolbarDecorator(JTable table) {
    myTable = table;
    myModel = table.getModel();
    myToolbarPosition = SystemInfo.isMac ? ActionToolbarPosition.BOTTOM : ActionToolbarPosition.RIGHT;
    myBorder = SystemInfo.isMac ? new CustomLineBorder(0,1,1,1) : null;
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = myModel instanceof RowEditableTableModel;
    if (myModel instanceof RowEditableTableModel) {
      createDefaultActions();
    }
  }

  private void createDefaultActions() {
    final JTable table = myTable;
    final RowEditableTableModel tableModel = (RowEditableTableModel)myModel;

    myAddAction = new Runnable() {
      public void run() {
        TableUtil.stopEditing(table);
        tableModel.addRow();
        final int index = tableModel.getRowCount() - 1;
        table.editCellAt(index, 0);
        table.setRowSelectionInterval(index, index);
        table.setColumnSelectionInterval(0, 0);
        table.getParent().repaint();
        final Component editorComponent = table.getEditorComponent();
        if (editorComponent != null) {
          final Rectangle bounds = editorComponent.getBounds();
          table.scrollRectToVisible(bounds);
          editorComponent.requestFocus();
        }
      }
    };

    myRemoveAction = new Runnable() {
      public void run() {
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 <= index && index < tableModel.getRowCount()) {
          tableModel.removeRow(index);
          if (index < tableModel.getRowCount()) {
            table.setRowSelectionInterval(index, index);
          }
          else {
            if (index > 0) {
              table.setRowSelectionInterval(index - 1, index - 1);
            }
          }
          updateButtons(table, tableModel, myPanel);
        }

        table.getParent().repaint();
        table.requestFocus();
      }
    };

    myUpAction = new Runnable() {
      public void run() {
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 < index && index < tableModel.getRowCount()) {
          tableModel.exchangeRows(index, index - 1);
          table.setRowSelectionInterval(index - 1, index - 1);
        }
        table.requestFocus();
      }
    };

    myDownAction = new Runnable() {
      public void run() {
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 <= index && index < tableModel.getRowCount() - 1) {
          tableModel.exchangeRows(index, index + 1);
          table.setRowSelectionInterval(index + 1, index + 1);
        }
        table.requestFocus();
      }
    };
   }

  private static void updateButtons(final JTable table,
                                    final RowEditableTableModel tableModel,
                                    final AddRemoveUpDownPanel p) {
    if (table.isEnabled()) {
      final int index = table.getSelectedRow();
      if (0 <= index && index < tableModel.getRowCount()) {
        final boolean downEnable = index < tableModel.getRowCount() - 1;
        final boolean upEnable = index > 0;
        if (p != null) {
          p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, true);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, upEnable);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, downEnable);
        }
      } else {
        if (p != null) {
          p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
          p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
        }
      }
      if (p != null) {
        p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, true);
      }
    }
  }


  public static TableToolbarDecorator createDecorator(JTable table) {
    return new TableToolbarDecorator(table);
  }

  public TableToolbarDecorator disableAddAction() {
    myAddActionEnabled = false;
    return this;
  }

  public TableToolbarDecorator disableRemoveAction() {
    myRemoveActionEnabled = false;
    return this;
  }

  public TableToolbarDecorator disableUpAction() {
    myUpActionEnabled = false;
    return this;
  }

  public TableToolbarDecorator disableDownAction() {
    myDownActionEnabled = false;
    return this;
  }

  public TableToolbarDecorator setToolbarBorder(Border border) {
    myBorder = border;
    return this;
  }

  public TableToolbarDecorator setLineBorder(int top, int left, int bottom, int right) {
    return setToolbarBorder(new CustomLineBorder(top, left, bottom, right));
  }

  public TableToolbarDecorator addExtraAction(AnActionButton action) {
    myExtraActions.add(action);
    return this;
  }

  public TableToolbarDecorator setToolbarPosition(ActionToolbarPosition position) {
    myToolbarPosition = position;
    return this;
  }

  public TableToolbarDecorator setAddAction(Runnable action) {
    myAddAction = action;
    return this;
  }

  public TableToolbarDecorator setRemoveAction(Runnable action) {
    myRemoveAction = action;
    return this;
  }

  public TableToolbarDecorator setUpAction(Runnable action) {
    myUpAction = action;
    return this;
  }

  public TableToolbarDecorator setDownAction(Runnable action) {
    myDownAction = action;
    return this;
  }

  public JPanel createPanel() {
    final AddRemoveUpDownPanel.Buttons[] buttons = getButtons();
    myPanel = new AddRemoveUpDownPanel(createListener(),
                             myTable,
                             myToolbarPosition == ActionToolbarPosition.TOP || myToolbarPosition == ActionToolbarPosition.BOTTOM,
                             myExtraActions.toArray(new AnActionButton[myExtraActions.size()]),
                             buttons);
    myPanel.setBorder(myBorder);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(myPanel, getPlacement());
    if (myModel instanceof RowEditableTableModel && buttons.length > 0) {
      updateButtons(myTable, (RowEditableTableModel)myModel, myPanel);

      if (myUpAction != null && myUpActionEnabled && myDownAction != null && myDownActionEnabled) {
        TableRowsDnDSupport.install(myTable, (RowEditableTableModel)myModel);
      }
      myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          updateButtons(myTable, (RowEditableTableModel)myModel, myPanel);
        }
      });
    }
    return panel;
  }

  private Object getPlacement() {
    switch (myToolbarPosition) {
      case TOP: return BorderLayout.NORTH;
      case LEFT: return BorderLayout.WEST;
      case BOTTOM: return BorderLayout.SOUTH;
      case RIGHT: return BorderLayout.EAST;
    }
    return BorderLayout.SOUTH;
  }

  private AddRemoveUpDownPanel.Buttons[] getButtons() {
    final ArrayList<AddRemoveUpDownPanel.Buttons> buttons = new ArrayList<AddRemoveUpDownPanel.Buttons>();
    if (myAddActionEnabled && myAddAction != null) {
      buttons.add(AddRemoveUpDownPanel.Buttons.ADD);
    }
    if (myRemoveActionEnabled && myRemoveAction != null) {
      buttons.add(AddRemoveUpDownPanel.Buttons.REMOVE);
    }
    if (myUpActionEnabled && myUpAction != null) {
      buttons.add(AddRemoveUpDownPanel.Buttons.UP);
    }
    if (myDownActionEnabled && myDownAction != null) {
      buttons.add(AddRemoveUpDownPanel.Buttons.DOWN);
    }
    return buttons.toArray(new AddRemoveUpDownPanel.Buttons[buttons.size()]);
  }

  private AddRemoveUpDownPanel.Listener createListener() {
    return new AddRemoveUpDownPanel.Listener() {
      @Override
      public void doAdd() {
        if (myAddAction != null) myAddAction.run();
      }

      @Override
      public void doRemove() {
        if (myRemoveAction != null) myRemoveAction.run();
      }

      @Override
      public void doUp() {
        if (myUpAction != null) myUpAction.run();
      }

      @Override
      public void doDown() {
        if (myDownAction != null) myDownAction.run();
      }
    };
  }
}
