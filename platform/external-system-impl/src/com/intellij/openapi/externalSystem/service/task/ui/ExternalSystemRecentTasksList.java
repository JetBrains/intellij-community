/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.task.ui;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 2:40 PM
 */
public class ExternalSystemRecentTasksList extends JBList implements Producer<ExternalTaskExecutionInfo> {

  @NotNull private static final JLabel EMPTY_RENDERER = new JLabel(" ");
  
  public ExternalSystemRecentTasksList(@NotNull ExternalSystemRecentTaskListModel model,
                                       @NotNull final ProjectSystemId externalSystemId,
                                       @NotNull final Project project)
  {
    super(model);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    Icon icon = null;
    if (manager instanceof ExternalSystemUiAware) {
      icon = ((ExternalSystemUiAware)manager).getTaskIcon();
    }
    if (icon == null) {
      icon = DefaultExternalSystemUiAware.INSTANCE.getTaskIcon();
    }
    setCellRenderer(new MyRenderer(project, icon, ExternalSystemUtil.findConfigurationType(externalSystemId)));
    setVisibleRowCount(ExternalSystemConstants.RECENT_TASKS_NUMBER);

    registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ExternalTaskExecutionInfo task = produce();
        if (task == null) {
          return;
        }
        ExternalSystemUtil.runTask(task.getSettings(), task.getExecutorId(), project, externalSystemId);
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() < 2) {
          return;
        }

        ExternalTaskExecutionInfo task = produce();
        if (task == null) {
          return;
        }

        ExternalSystemUtil.runTask(task.getSettings(), task.getExecutorId(), project, externalSystemId);
      }
    });
  }

  @Override
  public ExternalSystemRecentTaskListModel getModel() {
    return (ExternalSystemRecentTaskListModel)super.getModel();
  }

  public void setFirst(@NotNull ExternalTaskExecutionInfo task) {
    ExternalTaskExecutionInfo selected = produce();
    ExternalSystemRecentTaskListModel model = getModel();
    model.setFirst(task);
    clearSelection();
    if (selected == null) {
      return;
    }
    for (int i = 0; i < model.size(); i++) {
      //noinspection SuspiciousMethodCalls
      if (selected.equals(model.getElementAt(i))) {
        addSelectionInterval(i, i);
        return;
      }
    }
  }

  @Nullable
  @Override
  public ExternalTaskExecutionInfo produce() {
    int[] indices = getSelectedIndices();
    if (indices == null || indices.length != 1) {
      return null;
    }
    Object e = getModel().getElementAt(indices[0]);
    return e instanceof ExternalTaskExecutionInfo ? (ExternalTaskExecutionInfo)e : null;
  }

  private static class MyRenderer extends DefaultListCellRenderer {

    @NotNull private final Icon myGenericTaskIcon;
    @NotNull private final Project myProject;
    @Nullable private ConfigurationType myConfigurationType;

    MyRenderer(@NotNull Project project, @NotNull Icon genericTaskIcon, @Nullable ConfigurationType configurationType) {
      myProject = project;
      myGenericTaskIcon = genericTaskIcon;
      myConfigurationType = configurationType;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      Component renderer = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof ExternalSystemRecentTaskListModel.MyEmptyDescriptor) {
        return EMPTY_RENDERER;
      }
      else if (value instanceof ExternalTaskExecutionInfo) {
        ExternalTaskExecutionInfo taskInfo = (ExternalTaskExecutionInfo)value;
        String text = null;
        if (myConfigurationType != null) {
          List<RunConfiguration> configurations = RunManager.getInstance(myProject).getConfigurationsList(myConfigurationType);
          for (RunConfiguration configuration : configurations) {
            if (!(configuration instanceof ExternalSystemRunConfiguration)) {
              continue;
            }
            ExternalSystemRunConfiguration c = (ExternalSystemRunConfiguration)configuration;
            if (c.getSettings().equals(taskInfo.getSettings())) {
              text = c.getName();
            }
          }
        }
        if (StringUtil.isEmpty(text)) {
          text = AbstractExternalSystemTaskConfigurationType.generateName(myProject, taskInfo.getSettings());
        }
        
        setText(text);
        Icon icon = null;
        String executorId = taskInfo.getExecutorId();
        if (!StringUtil.isEmpty(executorId)) {
          Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
          if (executor != null) {
            icon = executor.getIcon();
          }
        }

        if (icon == null) {
          icon = myGenericTaskIcon;
        }
        setIcon(icon);
      }

      return renderer;
    }

    @Override
    public void setIcon(Icon icon) {
      if (icon != null) {
        // Don't allow to reset icon.
        super.setIcon(icon);
      }
    }
  }
}
