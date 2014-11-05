/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.util;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Observable;

/**
 * @author traff
 */
public abstract class ListTableWithButtons<T> extends Observable {
  private final List<T> myElements = ContainerUtil.newArrayList();
  private final JPanel myPanel;
  private final TableView<T> myTableView;
  private boolean myIsEnabled = true;

  protected ListTableWithButtons() {
    myTableView = new TableView(createListModel()) {
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
                SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    stopEditing();
                    int nextColumn = column < myTableView.getColumnCount() - 1? column + 1 : 0;
                    int nextRow = nextColumn == 0 ? row + 1 : row;
                    if (nextRow > myTableView.getRowCount() - 1) {
                      if (myElements.isEmpty() || !ListTableWithButtons.this.isEmpty(myElements.get(myElements.size() - 1))) {
                        ToolbarDecorator.findAddButton(myPanel).actionPerformed(null);
                        return;
                      } else {
                        nextRow = 0;
                      }
                    }
                    myTableView.editCellAt(nextRow, nextColumn);
                  }
                });
              }
            }
          });
        }
      }
    };
    myTableView.setRowHeight(new JTextField().getPreferredSize().height);
    myTableView.getTableViewModel().setSortable(false);
    myPanel = ToolbarDecorator.createDecorator(myTableView)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          if (!myElements.isEmpty() && isEmpty(myElements.get(myElements.size() - 1))) return;
          myTableView.stopEditing();
          setModified();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              myElements.add(createElement());
              myTableView.getTableViewModel().setItems(myElements);
              myTableView.scrollRectToVisible(myTableView.getCellRect(myElements.size() - 1, 0, true));
              myTableView.getComponent().editCellAt(myElements.size() - 1, 0);
            }
          });
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myTableView.stopEditing();
          setModified();
          T selected = getSelection();
          if (selected != null) {
            int selectedIndex = myElements.indexOf(selected);
            myElements.remove(selected);
            myTableView.getTableViewModel().setItems(myElements);

            int prev = selectedIndex - 1;
            if (prev >= 0) {
              myTableView.getComponent().getSelectionModel().setSelectionInterval(prev, prev);
            }
            else if (selectedIndex < myElements.size()) {
              myTableView.getComponent().getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
            }
          }
        }
      }).disableUpDownActions().addExtraActions(createExtraActions()).createPanel();

    ToolbarDecorator.findRemoveButton(myPanel).addCustomUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        T selection = getSelection();
        return selection != null && myIsEnabled && canDeleteElement(selection);
      }
    });
    ToolbarDecorator.findAddButton(myPanel).addCustomUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return myIsEnabled;
      }
    });


    myTableView.getComponent().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
    return myPanel;
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

  protected abstract T createElement();

  protected abstract boolean isEmpty(T element);

  @NotNull
  protected AnActionButton[] createExtraActions() {
    return new AnActionButton[0];
  }


  protected T getSelection() {
    int selIndex = myTableView.getComponent().getSelectionModel().getMinSelectionIndex();
    if (selIndex < 0) {
      return null;
    }
    else {
      return myElements.get(selIndex);
    }
  }

  public void setValues(List<T> envVariables) {
    myElements.clear();
    for (T envVariable : envVariables) {
      myElements.add(cloneElement(envVariable));
    }
    myTableView.getTableViewModel().setItems(myElements);
  }

  protected abstract T cloneElement(T variable);

  protected abstract boolean canDeleteElement(T selection);

  protected static abstract class ElementsColumnInfoBase<T> extends ColumnInfo<T, String> {
    private DefaultTableCellRenderer myRenderer;

    protected ElementsColumnInfoBase(String name) {
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
    protected abstract String getDescription(T element);
  }
}
