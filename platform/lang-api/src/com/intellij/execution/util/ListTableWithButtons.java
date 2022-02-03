// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.*;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.stream.Stream;

public abstract class ListTableWithButtons<T> extends Observable {
  private final List<T> myElements = new ArrayList<>();
  private JPanel myPanel;
  @NotNull private final TableView<T> myTableView;
  private final CommonActionsPanel myActionsPanel;
  private boolean myIsEnabled = true;
  private final ToolbarDecorator myDecorator;

  protected ListTableWithButtons() {
    myTableView = new TableView<T>(createListModel()) {
      @Override
      protected void createDefaultEditors() {
        super.createDefaultEditors();
        Object editor = defaultEditorsByColumnClass.get(String.class);
        if (editor instanceof DefaultCellEditor) {
          ((DefaultCellEditor)editor).getComponent().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
              final int column = myTableView.getEditingColumn();
              final int row = myTableView.getEditingRow();
              if (e.getModifiers() == 0 && (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_TAB)) {
                e.consume();
                SwingUtilities.invokeLater(() -> {
                  stopEditing();
                  int nextColumn = column < myTableView.getColumnCount() - 1 ? column + 1 : 0;
                  int nextRow = nextColumn == 0 ? row + 1 : row;
                  if (nextRow > myTableView.getRowCount() - 1) {
                    if (myElements.isEmpty() || !ListTableWithButtons.this.isEmpty(myElements.get(myElements.size() - 1))) {
                      AnActionButton addButton = ToolbarDecorator.findAddButton(myPanel);
                      if (addButton != null) {
                        addButton.actionPerformed(ActionUtil.createEmptyEvent());
                      }
                      return;
                    }
                    else {
                      nextRow = 0;
                    }
                  }
                  myTableView.scrollRectToVisible(myTableView.getCellRect(nextRow, nextColumn, true));
                  myTableView.editCellAt(nextRow, nextColumn);
                });
              }
            }
          });
        }
      }
    };
    myTableView.setShowGrid(false);
    myTableView.setIntercellSpacing(JBUI.emptySize());
    myTableView.getTableViewModel().setSortable(false);
    myTableView.getComponent().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    myDecorator = createToolbarDecorator();
    myActionsPanel = myDecorator.getActionsPanel();
  }

  protected ToolbarDecorator createToolbarDecorator() {
    return ToolbarDecorator.createDecorator(myTableView);
  }

  @Nullable
  protected AnActionButtonRunnable createRemoveAction() {
    return button -> removeSelected();
  }

  @Nullable
  protected AnActionButtonRunnable createAddAction() {
    return button -> addNewElement(createElement());
  }

  @Nullable
  protected AnActionButtonRunnable createEditAction() {
    return null;
  }

  protected void addNewElement(T newElement) {
    myTableView.stopEditing();
    setModified();
    SwingUtilities.invokeLater(() -> {
      if (myElements.isEmpty() || !isEmpty(myElements.get(myElements.size() - 1))) {
        myElements.add(newElement);
        myTableView.getTableViewModel().setItems(myElements);
      }
      myTableView.scrollRectToVisible(myTableView.getCellRect(myElements.size() - 1, 0, true));
      if (shouldEditRowOnCreation()) {
        myTableView.getComponent().editCellAt(myElements.size() - 1, 0);
      }
      myTableView.getComponent().revalidate();
      myTableView.getComponent().repaint();
    });
  }

  protected void removeSelected() {
    int[] selectedRows = myTableView.getComponent().getSelectedRows();
    if(selectedRows.length == 0)
      return;
    myTableView.stopEditing();
    setModified();
    int selectedIndex = myTableView.getSelectionModel().getLeadSelectionIndex();
    myTableView.scrollRectToVisible(myTableView.getCellRect(selectedIndex, 0, true));

    List<T> aliveElements = new ArrayList<>();
    for(int row = 0; row < myTableView.getRowCount(); ++row) {
      T selectedElement = myElements.get(row);
      if(!myTableView.isRowSelected(row) || !canDeleteElement(selectedElement)) {
        aliveElements.add(selectedElement);
      }
    }
    myElements.clear();
    myElements.addAll(aliveElements);

    myTableView.getSelectionModel().clearSelection();
    myTableView.getTableViewModel().setItems(myElements);

    int prev = selectedIndex - 1;
    if (prev >= 0) {
      myTableView.getComponent().getSelectionModel().setSelectionInterval(prev, prev);
    }
    else if (selectedIndex < myElements.size()) {
      myTableView.getComponent().getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
    }
  }

  @NotNull
  public TableView<T> getTableView() {
    return myTableView;
  }

  protected abstract ListTableModel createListModel();

  protected void setModified() {
    setChanged();
    notifyObservers();
  }

  protected List<T> getElements() {
    return myElements;
  }

  public JComponent getComponent() {
    if (myPanel == null) {
      myDecorator.setAddAction(createAddAction())
        .setRemoveAction(createRemoveAction())
        .setEditAction(createEditAction());
      if (!isUpDownSupported()) {
        myDecorator.disableUpDownActions();
      }
      myDecorator.addExtraActions(createExtraActions());
      myPanel = myDecorator.createPanel();
      configureToolbarButtons(myPanel);
    }
    return myPanel;
  }

  protected void configureToolbarButtons(@NotNull JPanel panel) {
    var addButton = ToolbarDecorator.findAddButton(panel);
    var removeButton = ToolbarDecorator.findRemoveButton(panel);
    var editButton = ToolbarDecorator.findEditButton(panel);
    var upButton = ToolbarDecorator.findUpButton(panel);
    var downButton = ToolbarDecorator.findDownButton(panel);

    Stream.of(addButton, removeButton, editButton, upButton, downButton)
      .filter(it -> it != null)
      .forEach(it -> it.addCustomUpdater(e -> myIsEnabled));

    if (removeButton != null) {
      removeButton.addCustomUpdater(e -> {
        List<T> selection = getSelection();
        if (selection.isEmpty()) return false;
        for (T t : selection) {
          if (!canDeleteElement(t)) return false;
        }
        return true;
      });
    }
  }

  public CommonActionsPanel getActionsPanel() {
    return myActionsPanel;
  }

  public void setEnabled() {
    myTableView.getComponent().setEnabled(true);
    myIsEnabled = true;
  }

  public void setDisabled() {
    myTableView.getComponent().setEnabled(false);
    myIsEnabled = false;
  }

  public void stopEditing() {
    myTableView.stopEditing();
  }

  public void refreshValues() {
    myTableView.getComponent().repaint();
  }

  protected void setSelection(T element) {
    myTableView.setSelection(Collections.singleton(element));
    TableUtil.scrollSelectionToVisible(myTableView);
  }

  protected void editSelection(int column) {
    List<T> selection = getSelection();
    if (selection.size() != 1) return;
    int row = myElements.indexOf(selection.get(0));
    if (row != -1) {
      TableUtil.editCellAt(myTableView, row, column);
    }
  }

  protected abstract T createElement();

  protected abstract boolean isEmpty(T element);

  protected AnActionButton @NotNull [] createExtraActions() {
    return new AnActionButton[0];
  }


  @NotNull
  protected List<T> getSelection() {
    int[] selection = myTableView.getComponent().getSelectedRows();
    if (selection.length == 0) {
      return Collections.emptyList();
    }
    else {
      List<T> result = new ArrayList<>(selection.length);
      for (int row : selection) {
        result.add(myElements.get(row));
      }
      return result;
    }
  }

  public void setValues(List<? extends T> envVariables) {
    myElements.clear();
    for (T envVariable : envVariables) {
      myElements.add(cloneElement(envVariable));
    }
    myTableView.getTableViewModel().setItems(myElements);
  }

  protected boolean isUpDownSupported() {
    return false;
  }

  protected boolean shouldEditRowOnCreation() {
    return true;
  }

  protected abstract T cloneElement(T variable);

  protected abstract boolean canDeleteElement(T selection);

  protected static abstract class ElementsColumnInfoBase<T> extends ColumnInfo<T, @NlsContexts.ListItem String> {
    private DefaultTableCellRenderer myRenderer;

    protected ElementsColumnInfoBase(@NlsContexts.ColumnName String name) {
      super(name);
    }

    @Override
    public TableCellRenderer getRenderer(T element) {
      if (myRenderer == null) {
        myRenderer = new DefaultTableCellRenderer();
      }
      if (element != null) {
        myRenderer.setText(valueOf(element));
        myRenderer.setToolTipText(getDescription(element));
      }
      return myRenderer;
    }

    @Nullable
    protected abstract @NlsContexts.Tooltip String getDescription(T element);
  }
}
