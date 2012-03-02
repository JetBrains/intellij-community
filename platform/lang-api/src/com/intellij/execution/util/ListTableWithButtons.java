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

import com.google.common.collect.Lists;
import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

/**
 * @author traff
 */
public abstract class ListTableWithButtons<T> extends Observable {
  private final List<T> myElements = Lists.newArrayList();
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final TableView myTableVeiw;
  private boolean myIsEnabled = true;

  protected ListTableWithButtons() {
    myTableVeiw = new TableView(createListModel());
    myTableVeiw.getTableViewModel().setSortable(false);
    myPanel.add(ScrollPaneFactory.createScrollPane(myTableVeiw.getComponent()), BorderLayout.CENTER);
    JComponent toolbarComponent = createToolbar();
    myPanel.add(toolbarComponent, BorderLayout.NORTH);
    myTableVeiw.getComponent().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  protected abstract ListTableModel createListModel();

  protected void setModified() {
    setChanged();
    notifyObservers();
  }

  protected JComponent createToolbar() {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new AddAction());
    actions.add(new DeleteAction());
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true).getComponent();
  }

  protected List<T> getElements() {
    return myElements;
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setEnabled() {
    myTableVeiw.getComponent().setEnabled(true);
    myIsEnabled = true;
  }

  public void setDisabled() {
    myTableVeiw.getComponent().setEnabled(false);
    myIsEnabled = false;
  }

  public void stopEditing() {
    myTableVeiw.stopEditing();
  }

  public void refreshValues() {
    myTableVeiw.getComponent().repaint();
  }

  protected abstract T createElement();


  protected T getSelection() {
    int selIndex = myTableVeiw.getComponent().getSelectionModel().getMinSelectionIndex();
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
    myTableVeiw.getTableViewModel().setItems(myElements);
  }

  protected abstract T cloneElement(T variable);

  private final class DeleteAction extends AnAction {
    public DeleteAction() {
      super(CommonBundle.message("button.delete"), null, IconLoader.getIcon("/general/remove.png"));
    }

    public void update(AnActionEvent e) {
      T selection = getSelection();
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(selection != null && myIsEnabled && canDeleteElement(selection));
    }

    public void actionPerformed(AnActionEvent e) {
      myTableVeiw.stopEditing();
      setModified();
      Object selected = getSelection();
      if (selected != null) {
        int selectedIndex = myElements.indexOf(selected);
        myElements.remove(selected);
        myTableVeiw.getTableViewModel().setItems(myElements);

        int prev = selectedIndex - 1;
        if (prev >= 0) {
          myTableVeiw.getComponent().getSelectionModel().setSelectionInterval(prev, prev);
        }
        else if (selectedIndex < myElements.size()) {
          myTableVeiw.getComponent().getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
        }
      }
    }
  }

  protected abstract boolean canDeleteElement(T selection);

  private final class AddAction extends AnAction {
    public AddAction() {
      super(CommonBundle.message("button.add"), null, IconLoader.getIcon("/general/add.png"));
    }

    public void update(AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(myIsEnabled);
    }

    public void actionPerformed(AnActionEvent e) {
      myTableVeiw.stopEditing();
      setModified();
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myElements.add(createElement());
          myTableVeiw.getTableViewModel().setItems(myElements);
          myTableVeiw.getComponent().editCellAt(myElements.size() - 1, 0);
        }
      });
    }
  }

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
