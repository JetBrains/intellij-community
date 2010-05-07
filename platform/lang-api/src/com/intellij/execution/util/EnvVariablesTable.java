/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

public class EnvVariablesTable extends Observable {
  private final List<EnvironmentVariable> myVariables = new ArrayList<EnvironmentVariable>();
  private final JPanel myPanel = new JPanel(new BorderLayout());

  private final ColumnInfo NAME = new ColumnInfo<EnvironmentVariable, String>("Name") {
    public String valueOf(EnvironmentVariable environmentVariable) {
      return environmentVariable.getName();
    }

    public Class getColumnClass() {
      return String.class;
    }

    public boolean isCellEditable(EnvironmentVariable environmentVariable) {
      return environmentVariable.getNameIsWriteable();
    }

    public void setValue(EnvironmentVariable environmentVariable, String s) {
      if (s.equals(valueOf(environmentVariable))) {
        return;
      }
      environmentVariable.setName(s);
      setModified();
    }
  };

  private final ColumnInfo VALUE = new ColumnInfo<EnvironmentVariable, String>("Value") {
    public String valueOf(EnvironmentVariable environmentVariable) {
      return environmentVariable.getValue();
    }

    public Class getColumnClass() {
      return String.class;
    }

    public boolean isCellEditable(EnvironmentVariable environmentVariable) {
      return !environmentVariable.getIsPredefined();
    }

    public void setValue(EnvironmentVariable environmentVariable, String s) {
      if (s.equals(valueOf(environmentVariable))) {
        return;
      }
      environmentVariable.setValue(s);
      setModified();
    }

  };
  private final TableView myTableVeiw;
  private boolean myIsEnabled = true;

  public EnvVariablesTable() {
    myTableVeiw = new TableView(new ListTableModel((new ColumnInfo[]{NAME, VALUE})));
    myTableVeiw.getTableViewModel().setSortable(false);
    myPanel.add(ScrollPaneFactory.createScrollPane(myTableVeiw.getComponent()), BorderLayout.CENTER);
    JComponent toolbarComponent = createToolbar();
    myPanel.add(toolbarComponent, BorderLayout.NORTH);
    myTableVeiw.getComponent().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  private void setModified() {
    setChanged();
    notifyObservers();
  }


  private JComponent createToolbar() {
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(new AddAction());
    actions.add(new DeleteAction());
    JComponent toolbarComponent = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, true).getComponent();
    return toolbarComponent;
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

  public void setValues(List<EnvironmentVariable> envVariables) {
    myVariables.clear();
    for (EnvironmentVariable envVariable : envVariables) {
      myVariables.add(envVariable.clone());
    }
    myTableVeiw.getTableViewModel().setItems(myVariables);
  }

  public List<EnvironmentVariable> getEnvironmentVariables() {
    return myVariables;
  }

  public void stopEditing() {
    myTableVeiw.stopEditing();
  }

  public void refreshValues() {
    myTableVeiw.getComponent().repaint();
  }

  private final class DeleteAction extends AnAction {
    public DeleteAction() {
      super(CommonBundle.message("button.delete"), null, IconLoader.getIcon("/general/remove.png"));
    }

    public void update(AnActionEvent e) {
      EnvironmentVariable selection = getSelection();
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(selection != null && myIsEnabled && !selection.getIsPredefined());
    }

    public void actionPerformed(AnActionEvent e) {
      myTableVeiw.stopEditing();
      setModified();
      EnvironmentVariable selected = getSelection();
      if (selected != null) {
        int selectedIndex = myVariables.indexOf(selected);
        myVariables.remove(selected);
        myTableVeiw.getTableViewModel().setItems(myVariables);

        int prev = selectedIndex - 1;
        if (prev >= 0) {
          myTableVeiw.getComponent().getSelectionModel().setSelectionInterval(prev, prev);
        }
        else if (selectedIndex < myVariables.size()) {
          myTableVeiw.getComponent().getSelectionModel().setSelectionInterval(selectedIndex, selectedIndex);
        }
      }

    }

  }

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
          myVariables.add(new EnvironmentVariable("", "", false));
          myTableVeiw.getTableViewModel().setItems(myVariables);
          myTableVeiw.getComponent().editCellAt(myVariables.size() - 1, 0);
        }
      });
    }
  }

  private EnvironmentVariable getSelection() {
    int selIndex = myTableVeiw.getComponent().getSelectionModel().getMinSelectionIndex();
    if (selIndex < 0) {
      return null;
    }
    else {
      return myVariables.get(selIndex);
    }
  }

}
