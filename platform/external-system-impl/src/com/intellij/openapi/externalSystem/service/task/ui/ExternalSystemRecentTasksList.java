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
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.serialization.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.service.ui.DefaultExternalSystemUiAware;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 2:40 PM
 */
public class ExternalSystemRecentTasksList extends JBList implements Producer<ExternalTaskPojo> {

  @NotNull private static final JLabel EMPTY_RENDERER = new JLabel(" ");

  public ExternalSystemRecentTasksList(@NotNull ExternalSystemRecentTaskListModel model, @NotNull ProjectSystemId externalSystemId) {
    super(model);
    
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    Icon icon = null;
    if (manager instanceof ExternalSystemUiAware) {
      icon = ((ExternalSystemUiAware)manager).getTaskIcon();
    }
    if (icon == null) {
      icon = DefaultExternalSystemUiAware.INSTANCE.getTaskIcon();
    }
    setCellRenderer(new MyRenderer(icon));
    setVisibleRowCount(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  @Override
  public ExternalSystemRecentTaskListModel getModel() {
    return (ExternalSystemRecentTaskListModel)super.getModel();
  }

  public void setFirst(@NotNull ExternalTaskPojo task) {
    ExternalTaskPojo selected = produce();
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
  public ExternalTaskPojo produce() {
    int[] indices = getSelectedIndices();
    if (indices == null || indices.length != 1) {
      return null;
    }
    Object e = getModel().getElementAt(indices[0]);
    return e instanceof ExternalTaskPojo ? (ExternalTaskPojo)e : null;
  }

  private static class MyRenderer extends DefaultListCellRenderer {
    
    @NotNull private final Icon myGenericTaskIcon;

    MyRenderer(@NotNull Icon genericTaskIcon) {
      myGenericTaskIcon = genericTaskIcon;
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof ExternalSystemRecentTaskListModel.MyEmptyDescriptor) {
        return EMPTY_RENDERER;
      }
      else if (value instanceof ExternalTaskPojo) {
        ExternalTaskPojo task = (ExternalTaskPojo)value;
        setText(task.getName());
        Icon icon = null;
        String executorId = task.getExecutorId();
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
      return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
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
