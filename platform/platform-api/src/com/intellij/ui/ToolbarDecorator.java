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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.border.CustomLineBorder;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.ElementProducer;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnusedDeclaration")
public class ToolbarDecorator implements DataProvider, AddRemoveUpDownPanel.ListenerFactory {
  private JTable myTable;
  private JTree myTree;
  private TableModel myTableModel;
  private ListModel myListModel;
  private Border myToolbarBorder;
  private boolean myAddActionEnabled;
  private boolean myRemoveActionEnabled;
  private boolean myUpActionEnabled;
  private boolean myDownActionEnabled;
  private Border myBorder;
  private List<AnActionButton> myExtraActions = new ArrayList<AnActionButton>();
  private ActionToolbarPosition myToolbarPosition;
  private AnActionButtonRunnable myAddAction;
  private AnActionButtonRunnable myRemoveAction;
  private AnActionButtonRunnable myUpAction;
  private AnActionButtonRunnable myDownAction;
  private String myAddName;
  private String myRemoveName;
  private String myMoveUpName;
  private String myMoveDownName;
  private AddRemoveUpDownPanel myPanel;
  private JList myList;

  private static final Comparator<AnAction> ACTION_BUTTONS_SORTER = new Comparator<AnAction>() {
    @Override
    public int compare(AnAction a1, AnAction a2) {
      if (a1 instanceof AnActionButton && a2 instanceof AnActionButton) {
        final JComponent c1 = ((AnActionButton)a1).getContextComponent();
        final JComponent c2 = ((AnActionButton)a2).getContextComponent();
        return c1.hasFocus() ? -1 : c2.hasFocus() ? 1 : 0;
      }
      return 0;
    }
  };  

  private ToolbarDecorator(JTable table) {
    myTable = table;
    myTableModel = table.getModel();
    initPositionAndBorder();
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = myTableModel instanceof EditableModel;
    if (myTableModel instanceof EditableModel) {
      createDefaultTableActions(null);
    }
  }

  private ToolbarDecorator(JList list) {
    myList = list;
    myListModel = list.getModel();
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = true;
    initPositionAndBorder();
    createDefaultListActions();
  }

  private <T> ToolbarDecorator(TableView<T> table, ElementProducer<T> producer) {
    myTable = table;
    myTableModel = table.getListTableModel();
    initPositionAndBorder();
    myAddActionEnabled = myRemoveActionEnabled = myUpActionEnabled = myDownActionEnabled = myTableModel instanceof ListTableModel;
    if (myTableModel instanceof ListTableModel) {
      createDefaultTableActions(producer);
    }
  }

  public ToolbarDecorator(JTree tree) {
    myTree = tree;
    initPositionAndBorder();
  }

  private void createDefaultListActions() {
    myRemoveAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.removeSelectedItems(myList);
        updateListButtons(myList, myPanel);
      }
    };
    myUpAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.moveSelectedItemsUp(myList);
        updateListButtons(myList, myPanel);
      }
    };
    myDownAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        ListUtil.moveSelectedItemsDown(myList);
        updateListButtons(myList, myPanel);
      }
    };
  }

  private void initPositionAndBorder() {
    myToolbarPosition = SystemInfo.isMac ? ActionToolbarPosition.BOTTOM : myTree == null ? ActionToolbarPosition.RIGHT : ActionToolbarPosition.TOP;
    myBorder = SystemInfo.isMac ? new CustomLineBorder(0,1,1,1) : new CustomLineBorder(0, 1, 0, 0);
    if (myTable != null) {
      myTable.setBorder(IdeBorderFactory.createEmptyBorder(0));
    }
    if (myTree != null) {
      myTree.setBorder(IdeBorderFactory.createEmptyBorder(0));
    }
  }

  private void createDefaultTableActions(@Nullable final ElementProducer<?> producer) {
    final JTable table = myTable;
    final EditableModel tableModel = (EditableModel)myTableModel;

    myAddAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int rowCount = table.getRowCount();
        if (tableModel instanceof ListTableModel && producer != null) {
          //noinspection unchecked
          ((ListTableModel)tableModel).addRow(producer.createElement());
        } else {
          tableModel.addRow();
        }
        if (rowCount == table.getRowCount()) return;
        final int index = myTableModel.getRowCount() - 1;
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

    myRemoveAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        int index = table.getSelectedRow();
        if (0 <= index && index < myTableModel.getRowCount()) {
          tableModel.removeRow(index);
          if (index < myTableModel.getRowCount()) {
            table.setRowSelectionInterval(index, index);
          }
          else {
            if (index > 0) {
              table.setRowSelectionInterval(index - 1, index - 1);
            }
          }
          updateTableButtons(table, tableModel, myPanel);
        }

        table.getParent().repaint();
        table.requestFocus();
      }
    };

    myUpAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int[] indexes = table.getSelectedRows();
        for (int index : indexes) {
          if (0 < index && index < myTableModel.getRowCount()) {
            tableModel.exchangeRows(index, index - 1);
            table.setRowSelectionInterval(index - 1, index - 1);
          }
        }
        table.requestFocus();
      }
    };

    myDownAction = new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        TableUtil.stopEditing(table);
        final int[] indexes = table.getSelectedRows();
        for (int index : indexes) {
          if (0 <= index && index < myTableModel.getRowCount() - 1) {
            tableModel.exchangeRows(index, index + 1);
            table.setRowSelectionInterval(index + 1, index + 1);
          }
        }
        table.requestFocus();
      }
    };
   }

  private static void updateListButtons(final JList list, final AddRemoveUpDownPanel p) {
    if (list.isEnabled() && p != null) {
      final int index = list.getSelectedIndex();
      if (0 <= index && index < list.getModel().getSize()) {
        final boolean downEnable = list.getMaxSelectionIndex() < list.getModel().getSize() - 1;
        final boolean upEnable = list.getMinSelectionIndex() > 0;
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, true);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, upEnable);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, downEnable);
      } else {
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
      }
      p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, true);
    }
  }

  private static void updateTableButtons(final JTable table,
                                         final EditableModel tableModel,
                                         final AddRemoveUpDownPanel p) {
    if (table.isEnabled() && p != null) {
      final int index = table.getSelectedRow();
      final int size = ((TableModel)tableModel).getRowCount();
      if (0 <= index && index < size) {
        final boolean downEnable = table.getSelectionModel().getMaxSelectionIndex() < size - 1;
        final boolean upEnable = table.getSelectionModel().getMinSelectionIndex() > 0;
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, true);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, upEnable);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, downEnable);
      } else {
        p.setEnabled(AddRemoveUpDownPanel.Buttons.REMOVE, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.UP, false);
        p.setEnabled(AddRemoveUpDownPanel.Buttons.DOWN, false);
      }
      p.setEnabled(AddRemoveUpDownPanel.Buttons.ADD, true);
    }
  }


  public static ToolbarDecorator createDecorator(@NotNull JTable table) {
    return new ToolbarDecorator(table);
  }
  
  public static ToolbarDecorator createDecorator(@NotNull JTree tree) {
    return new ToolbarDecorator(tree);
  }

  public static ToolbarDecorator createDecorator(@NotNull JList list) {
    return new ToolbarDecorator(list);
  }

  public static <T> ToolbarDecorator  createDecorator(@NotNull TableView<T> table, ElementProducer<T> producer) {
    return new ToolbarDecorator(table, producer);
  }

  public ToolbarDecorator disableAddAction() {
    myAddActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableRemoveAction() {
    myRemoveActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableUpAction() {
    myUpActionEnabled = false;
    return this;
  }

  public ToolbarDecorator disableDownAction() {
    myDownActionEnabled = false;
    return this;
  }

  public ToolbarDecorator setToolbarBorder(Border border) {
    myBorder = border;
    return this;
  }

  public ToolbarDecorator setLineBorder(int top, int left, int bottom, int right) {
    return setToolbarBorder(new CustomLineBorder(top, left, bottom, right));
  }

  public ToolbarDecorator addExtraAction(AnActionButton action) {
    myExtraActions.add(action);
    return this;
  }

  public ToolbarDecorator setToolbarPosition(ActionToolbarPosition position) {
    myToolbarPosition = position;
    return this;
  }

  public ToolbarDecorator setAddAction(AnActionButtonRunnable action) {
    myAddActionEnabled = action != null;
    myAddAction = action;
    return this;
  }

  public ToolbarDecorator setRemoveAction(AnActionButtonRunnable action) {
    myRemoveActionEnabled = action != null;
    myRemoveAction = action;
    return this;
  }

  public ToolbarDecorator setUpAction(AnActionButtonRunnable action) {
    myUpActionEnabled = action != null;
    myUpAction = action;
    return this;
  }

  public ToolbarDecorator setDownAction(AnActionButtonRunnable action) {
    myDownActionEnabled = action != null;
    myDownAction = action;
    return this;
  }

  public ToolbarDecorator setAddActionName(String name) {
    myAddName = name;
    return this;
  }

  public ToolbarDecorator setRemoveActionName(String name) {
    myRemoveName = name;
    return this;
  }

  public ToolbarDecorator setMoveUpActionName(String name) {
    myMoveUpName = name;
    return this;
  }

  public ToolbarDecorator setMoveDownActionName(String name) {
    myMoveDownName = name;
    return this;
  }

  public JPanel createPanel() {
    final AddRemoveUpDownPanel.Buttons[] buttons = getButtons();
    myPanel = new AddRemoveUpDownPanel(this,
                                       myTable == null ? myList == null ? myTree : myList : myTable,
                             myToolbarPosition == ActionToolbarPosition.TOP || myToolbarPosition == ActionToolbarPosition.BOTTOM,
                             myExtraActions.toArray(new AnActionButton[myExtraActions.size()]),
                             myAddName, myRemoveName, myMoveUpName, myMoveDownName,
                             buttons);
    myPanel.setBorder(myBorder);
    final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable == null ? myList == null ? myTree : myList : myTable);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder(0));
    final JPanel panel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        if (myList != null) {
          updateListButtons(myList, myPanel);
        }
        if (myTable != null && myTableModel instanceof EditableModel) {
          updateTableButtons(myTable, (EditableModel)myTableModel, myPanel);
        }
      }
    };
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(myPanel, getPlacement());
    if (myTableModel instanceof EditableModel && buttons.length > 0) {
      updateTableButtons(myTable, (EditableModel)myTableModel, myPanel);

      if (myUpAction != null && myUpActionEnabled
          && myDownAction != null && myDownActionEnabled
          && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
        TableRowsDnDSupport.install(myTable, (EditableModel)myTableModel);
      }
      myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          updateTableButtons(myTable, (EditableModel)myTableModel, myPanel);
        }
      });
    }
    if (myList != null) {
      updateListButtons(myList, myPanel);
      myList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          updateListButtons(myList, myPanel);
        }
      });
    }
    panel.setBorder(new LineBorder(UIUtil.getBorderColor()));
    panel.putClientProperty(ActionToolbar.ACTION_TOOLBAR_PROPERTY_KEY, myPanel.getComponent(0));
    DataManager.registerDataProvider(panel, this);
    return panel;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.ACTIONS_SORTER.is(dataId)) {
      return ACTION_BUTTONS_SORTER;
    }
    return null;
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

  public AddRemoveUpDownPanel.Listener createListener(final AddRemoveUpDownPanel panel) {
    return new AddRemoveUpDownPanel.Listener() {
      @Override
      public void doAdd() {
        if (myAddAction != null) {
          myAddAction.run(panel.getAnActionButton(AddRemoveUpDownPanel.Buttons.ADD));
        }
      }

      @Override
      public void doRemove() {
        if (myRemoveAction != null) {
          myRemoveAction.run(panel.getAnActionButton(AddRemoveUpDownPanel.Buttons.REMOVE));
        }
      }

      @Override
      public void doUp() {
        if (myUpAction != null) {
          myUpAction.run(panel.getAnActionButton(AddRemoveUpDownPanel.Buttons.UP));
        }
      }

      @Override
      public void doDown() {
        if (myDownAction != null) {
          myDownAction.run(panel.getAnActionButton(AddRemoveUpDownPanel.Buttons.DOWN));
        }
      }
    };
  }
  
  public static AnActionButton findAddButton(@NotNull JComponent container) {
    return findButton(container, AddRemoveUpDownPanel.Buttons.ADD);
  }

  public static AnActionButton findRemoveButton(@NotNull JComponent container) {
    return findButton(container, AddRemoveUpDownPanel.Buttons.REMOVE);
  }

  public static AnActionButton findUpButton(@NotNull JComponent container) {
    return findButton(container, AddRemoveUpDownPanel.Buttons.UP);
  }

  public static AnActionButton findDownButton(@NotNull JComponent container) {
    return findButton(container, AddRemoveUpDownPanel.Buttons.DOWN);
  }


  private static AnActionButton findButton(JComponent comp, AddRemoveUpDownPanel.Buttons type) {
    final AddRemoveUpDownPanel panel = UIUtil.findComponentOfType(comp, AddRemoveUpDownPanel.class);
    if (panel != null) {
      return panel.getAnActionButton(type);
    }
    return null;
  }
}
