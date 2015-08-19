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

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
    final JPanel tasksPanel = ToolbarDecorator.createDecorator(myTree)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          //todo temp
          final RunnerAndConfigurationSettings load = ((RunManagerEx)RunManager.getInstance(myProject)).findConfigurationByName("Load");
          if (load != null) {
            final ProjectStartupTasksTreeModel oldModel = (ProjectStartupTasksTreeModel)myTree.getModel();
            final List<RunnerAndConfigurationSettings> configurations = oldModel.getConfigurations();
            if (! configurations.contains(load)) {
              configurations.add(load);
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
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          //todo
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          //todo
        }
      })
      .createPanel();
    return FormBuilder.createFormBuilder() // todo bundle
      .addLabeledComponentFillVertically("Tasks to be executed right after opening the project.", tasksPanel)
      .getPanel();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {

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
          // todo correct renderer
          final String name = ((RunnerAndConfigurationSettings)value).getName();
          append(name);
        }
      }
    });
  }
}
