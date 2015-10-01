/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.startup;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private JBTable myTable;
  private ProjectStartupTaskManager myProjectStartupTaskManager;
  private ToolbarDecorator myDecorator;
  private ProjectStartupTasksTableModel myModel;

  public ProjectStartupConfigurable(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return "preferences.startup.tasks";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Startup Tasks";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    initManager();

    myModel = new ProjectStartupTasksTableModel(RunManagerEx.getInstanceEx(myProject));
    myTable = new JBTable(myModel);
    new TableSpeedSearch(myTable);
    DefaultCellEditor defaultEditor = (DefaultCellEditor)myTable.getDefaultEditor(Object.class);
    defaultEditor.setClickCountToStart(1);

    myTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    new DumbAwareAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        final int row = myTable.getSelectedRow();
        if (row >= 0 && myModel.isCellEditable(row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN)) {
          myModel.setValueAt(!Boolean.TRUE.equals(myTable.getValueAt(row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN)),
                             row, ProjectStartupTasksTableModel.IS_SHARED_COLUMN);
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyEvent.VK_SPACE), myTable);

    installRenderers();
    myDecorator = ToolbarDecorator.createDecorator(myTable)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          selectAndAddConfiguration(button);
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final int row = myTable.getSelectedRow();
          if (row < 0) return;
          final RunnerAndConfigurationSettings selected = myModel.getAllConfigurations().get(row);

          final RunManager runManager = RunManagerImpl.getInstance(myProject);
          final RunnerAndConfigurationSettings was = runManager.getSelectedConfiguration();
          try {
            runManager.setSelectedConfiguration(selected);
            new EditConfigurationsDialog(myProject).showAndGet();
          } finally {
            runManager.setSelectedConfiguration(was);
          }
          myModel.fireTableDataChanged();
          selectPathOrFirst(selected);
        }
      })
      .setEditActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return myTable.getSelectedRow() >= 0;
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final int row = myTable.getSelectedRow();
          if (row < 0) return;

          myModel.removeRow(row);
          selectPathOrFirst(null);
        }
      }).disableUpAction().disableDownAction();
    final JPanel tasksPanel = myDecorator.createPanel();
    return FormBuilder.createFormBuilder() // todo bundle
      .addLabeledComponentFillVertically("Tasks to be executed right after opening the project.", tasksPanel)
      .getPanel();
  }

  private void initManager() {
    if (myProjectStartupTaskManager == null) {
      myProjectStartupTaskManager = ProjectStartupTaskManager.getInstance(myProject);
    }
  }

  private void selectPathOrFirst(RunnerAndConfigurationSettings settings) {
    if (myTable.isEmpty()) return;

    if (settings != null) {
      final List<RunnerAndConfigurationSettings> configurations = myModel.getAllConfigurations();
      for (int i = 0; i < configurations.size(); i++) {
        final RunnerAndConfigurationSettings configuration = configurations.get(i);
        if (configuration == settings) {
          TableUtil.selectRows(myTable, new int[]{i});
          return;
        }
      }
    }
    TableUtil.selectRows(myTable, new int[]{0});
    myTable.getSelectionModel().setLeadSelectionIndex(0);
  }

  private ChooseRunConfigurationPopup.ItemWrapper<Void> createEditWrapper() {
    return new ChooseRunConfigurationPopup.ItemWrapper<Void>(null) {
      @Override
      public Icon getIcon() {
        return AllIcons.Actions.EditSource;
      }

      @Override
      public String getText() {
        return UIUtil.removeMnemonic(ActionsBundle.message("action.editRunConfigurations.text"));
      }

      @Override
      public void perform(@NotNull final Project project, @NotNull final Executor executor, @NotNull DataContext context) {
        if (new EditConfigurationsDialog(project).showAndGet()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              RunnerAndConfigurationSettings configuration = RunManager.getInstance(project).getSelectedConfiguration();
              if (configuration != null) {
                myModel.addConfiguration(configuration);
                selectPathOrFirst(configuration);
              }
            }
          }, project.getDisposed());
        }
      }

      @Override
      public boolean available(Executor executor) {
        return true;
      }
    };
  }

  private void selectAndAddConfiguration(final AnActionButton button) {
    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final List<ChooseRunConfigurationPopup.ItemWrapper> wrappers = new ArrayList<ChooseRunConfigurationPopup.ItemWrapper>();
    wrappers.add(createEditWrapper());
    final ChooseRunConfigurationPopup.ItemWrapper[] allSettings =
      ChooseRunConfigurationPopup.createSettingsList(myProject, new ExecutorProvider() {
        @Override
        public Executor getExecutor() {
          return executor;
        }
      }, false);
    for (ChooseRunConfigurationPopup.ItemWrapper setting : allSettings) {
      if (setting.getValue() instanceof RunnerAndConfigurationSettings) {
        // todo maybe auto save temporary?
        if (!((RunnerAndConfigurationSettings)setting.getValue()).isTemporary()) {
          wrappers.add(setting);
        }
      }
    }
    final JBList list = new JBList(wrappers);
    list.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof ChooseRunConfigurationPopup.ItemWrapper) {
          setIcon(((ChooseRunConfigurationPopup.ItemWrapper)value).getIcon());
          append(((ChooseRunConfigurationPopup.ItemWrapper)value).getText());
        }
      }
    });
    final JBPopup popup = PopupFactoryImpl.getInstance()
      .createListPopupBuilder(list)
      .setItemChoosenCallback(new Runnable() {
        @Override
        public void run() {
          final int index = list.getSelectedIndex();
          if (index < 0) return;
          final ChooseRunConfigurationPopup.ItemWrapper at = (ChooseRunConfigurationPopup.ItemWrapper)list.getModel().getElementAt(index);
          if (at.getValue() instanceof RunnerAndConfigurationSettings) {
            final RunnerAndConfigurationSettings added = (RunnerAndConfigurationSettings)at.getValue();
            myModel.addConfiguration(added);
            selectPathOrFirst(added);
          } else {
            at.perform(myProject, executor, button.getDataContext());
          }
        }
      })
      .createPopup();

    final RelativePoint point = button.getPreferredPopupPoint();
    if (point != null) {
      popup.show(point);
    } else {
      popup.showInCenterOf(myDecorator.getActionsPanel());
    }
  }

  @Override
  public boolean isModified() {
    initManager();
    final Set<RunnerAndConfigurationSettings> shared = new HashSet<RunnerAndConfigurationSettings>(myProjectStartupTaskManager.getSharedConfigurations());
    final List<RunnerAndConfigurationSettings> list = new ArrayList<RunnerAndConfigurationSettings>(shared);
    list.addAll(myProjectStartupTaskManager.getLocalConfigurations());
    Collections.sort(list, ProjectStartupTasksTableModel.RunnerAndConfigurationSettingsComparator.getInstance());

    if (!Comparing.equal(list, myModel.getAllConfigurations())) return true;
    if (!Comparing.equal(shared, myModel.getSharedConfigurations())) return true;
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    initManager();
    final List<RunnerAndConfigurationSettings> shared = new ArrayList<RunnerAndConfigurationSettings>();
    final List<RunnerAndConfigurationSettings> local = new ArrayList<RunnerAndConfigurationSettings>();

    final Set<RunnerAndConfigurationSettings> sharedSet = myModel.getSharedConfigurations();
    final List<RunnerAndConfigurationSettings> allConfigurations = myModel.getAllConfigurations();
    for (RunnerAndConfigurationSettings configuration : allConfigurations) {
      if (sharedSet.contains(configuration)) {
        shared.add(configuration);
      } else {
        local.add(configuration);
      }
    }

    myProjectStartupTaskManager.setStartupConfigurations(shared, local);
  }

  @Override
  public void reset() {
    initManager();
    myModel.setData(myProjectStartupTaskManager.getSharedConfigurations(), myProjectStartupTaskManager.getLocalConfigurations());
    selectPathOrFirst(null);
  }

  @Override
  public void disposeUIResources() {
  }

  private void installRenderers() {
    final TableColumn checkboxColumn = myTable.getColumnModel().getColumn(ProjectStartupTasksTableModel.IS_SHARED_COLUMN);
    final String header = checkboxColumn.getHeaderValue().toString();
    final FontMetrics fm = myTable.getFontMetrics(myTable.getTableHeader().getFont());
    final int width = - new JBCheckBox().getPreferredSize().width + fm.stringWidth(header + "ww");
    TableUtil.setupCheckboxColumn(checkboxColumn, width);
    checkboxColumn.setCellRenderer(new BooleanTableCellRenderer());

    myTable.getTableHeader().setResizingAllowed(false);
    myTable.getTableHeader().setReorderingAllowed(false);

    final TableColumn nameColumn = myTable.getColumnModel().getColumn(ProjectStartupTasksTableModel.NAME_COLUMN);
    nameColumn.setCellRenderer(new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
        final RunnerAndConfigurationSettings settings = myModel.getAllConfigurations().get(row);
        setIcon(settings.getConfiguration().getIcon());
        append(settings.getName());
      }
    });
  }
}
