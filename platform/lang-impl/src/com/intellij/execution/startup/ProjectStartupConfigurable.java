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
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ChooseRunConfigurationPopup;
import com.intellij.execution.actions.ExecutorProvider;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Irina.Chernushina on 8/19/2015.
 */
public class ProjectStartupConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final Project myProject;
  private Tree myTree;
  private ProjectStartupConfiguration myConfiguration;
  private ToolbarDecorator myDecorator;

  public ProjectStartupConfigurable(Project project) {
    myProject = project;
    myConfiguration = ProjectStartupConfiguration.getInstance(myProject);
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
    return "Project Startup Tasks";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    myTree = new Tree();
    installRenderer();
    myDecorator = ToolbarDecorator.createDecorator(myTree)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          selectAndAddConfiguration(button);
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          RunnerAndConfigurationSettings selected = getSelectedSettings();
          if (selected == null) return;

          final RunManager runManager = RunManagerImpl.getInstance(myProject);
          final RunnerAndConfigurationSettings was = runManager.getSelectedConfiguration();
          try {
            runManager.setSelectedConfiguration(selected);
            new EditConfigurationsDialog(myProject).showAndGet();
          } finally {
            runManager.setSelectedConfiguration(was);
          }
          setModel(new ProjectStartupTasksTreeModel(((ProjectStartupTasksTreeModel) myTree.getModel()).getConfigurations()));
          selectPathOrFirst(selected);
        }
      })
      .setEditActionUpdater(new AnActionButtonUpdater() {
        @Override
        public boolean isEnabled(AnActionEvent e) {
          return getSelectedSettings() != null;
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          RunnerAndConfigurationSettings selected = getSelectedSettings();
          if (selected == null) return;
          final ProjectStartupTasksTreeModel oldModel = (ProjectStartupTasksTreeModel)myTree.getModel();
          final List<RunnerAndConfigurationSettings> configurations = oldModel.getConfigurations();
          if (!configurations.contains(selected)) {
            return;
          }
          configurations.remove(selected);
          Collections.sort(configurations, new Comparator<RunnerAndConfigurationSettings>() {
            @Override
            public int compare(RunnerAndConfigurationSettings o1, RunnerAndConfigurationSettings o2) {
              return o1.getName().compareToIgnoreCase(o2.getName());
            }
          });
          setModel(new ProjectStartupTasksTreeModel(configurations));
          selectPathOrFirst(null);
        }
      });
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    final JPanel tasksPanel = myDecorator.createPanel();
    return FormBuilder.createFormBuilder() // todo bundle
      .addLabeledComponentFillVertically("Tasks to be executed right after opening the project.", tasksPanel)
      .getPanel();
  }

  private void selectPathOrFirst(RunnerAndConfigurationSettings settings) {
    if (myTree.isEmpty()) return;
    myTree.clearSelection();
    if (settings != null) {
      final List<RunnerAndConfigurationSettings> configurations = ((ProjectStartupTasksTreeModel)myTree.getModel()).getConfigurations();
      for (int i = 0; i < configurations.size(); i++) {
        final RunnerAndConfigurationSettings configuration = configurations.get(i);
        if (configuration == settings) {
          myTree.setSelectionRow(i);
          return;
        }
      }
    }
    myTree.addSelectionRow(0);
  }

  @Nullable
  private RunnerAndConfigurationSettings getSelectedSettings() {
    final TreePath path = myTree.getSelectionPath();
    if (path == null) return null;
    if (! (path.getLastPathComponent() instanceof RunnerAndConfigurationSettings)) return null;
    return (RunnerAndConfigurationSettings)path.getLastPathComponent();
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
                addConfigurationToList(configuration);
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
    final Executor executor = ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
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
        if (! ((RunnerAndConfigurationSettings)setting.getValue()).isTemporary()) {
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
            addConfigurationToList(added);
            selectPathOrFirst(added);
          } else {
            at.perform(myProject, executor, button.getDataContext());
          }
        }
      })
      .createPopup();
    popup.show(new RelativePoint(myDecorator.getActionsPanel(), new Point(10, 10)));
  }

  private void addConfigurationToList(final RunnerAndConfigurationSettings settings) {
    if (settings != null) {
      final ProjectStartupTasksTreeModel oldModel = (ProjectStartupTasksTreeModel)myTree.getModel();
      final List<RunnerAndConfigurationSettings> configurations = oldModel.getConfigurations();
      if (!configurations.contains(settings)) {
        configurations.add(settings);
      }
      Collections.sort(configurations, new Comparator<RunnerAndConfigurationSettings>() {
        @Override
        public int compare(RunnerAndConfigurationSettings o1, RunnerAndConfigurationSettings o2) {
          return o1.getName().compareToIgnoreCase(o2.getName());
        }
      });
      setModel(new ProjectStartupTasksTreeModel(configurations));
    }
  }

  @Override
  public boolean isModified() {
    final List<RunnerAndConfigurationSettings> recorded = myConfiguration.getStartupConfigurations();
    final List<RunnerAndConfigurationSettings> current = ((ProjectStartupTasksTreeModel)myTree.getModel()).getConfigurations();
    return ! Comparing.equal(recorded, current);
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.setStartupConfigurations(((ProjectStartupTasksTreeModel)myTree.getModel()).getConfigurations(), false);
  }

  @Override
  public void reset() {
    final ProjectStartupTasksTreeModel model = new ProjectStartupTasksTreeModel(myConfiguration.getStartupConfigurations());
    setModel(model);
  }

  private void setModel(ProjectStartupTasksTreeModel model) {
    myTree.setModel(model);
    myTree.setShowsRootHandles(false);
    myTree.setRootVisible(false);
  }

  @Override
  public void disposeUIResources() {

  }

  private void installRenderer() {
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof RunnerAndConfigurationSettings) {
          final RunnerAndConfigurationSettings settings = (RunnerAndConfigurationSettings)value;
          setIcon(settings.getConfiguration().getIcon());
          append(settings.getName());
        }
      }
    });
  }
}
