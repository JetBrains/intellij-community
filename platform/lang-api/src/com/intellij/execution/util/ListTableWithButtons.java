/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.util.List;
import java.util.Observable;

/**
 * @author traff
 */
public abstract class ListTableWithButtons<T> extends Observable {
  private final List<T> myElements = ContainerUtil.newArrayList();
  private final JPanel myPanel;
  private final TableView myTableView;
  private boolean myIsEnabled = true;

  protected ListTableWithButtons() {
    myTableView = new TableView(createListModel());
    myTableView.getTableViewModel().setSortable(false);
    myPanel = ToolbarDecorator.createDecorator(myTableView)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myTableView.stopEditing();
          setModified();
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              myElements.add(createElement());
              myTableView.getTableViewModel().setItems(myElements);
              myTableView.getComponent().editCellAt(myElements.size() - 1, 0);
            }
          });
        }
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          myTableView.stopEditing();
          setModified();
          Object selected = getSelection();
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
      }).disableUpDownActions().createPanel();

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
