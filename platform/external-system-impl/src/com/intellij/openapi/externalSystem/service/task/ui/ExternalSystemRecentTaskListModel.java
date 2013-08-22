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

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskExecutionInfo;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 6/7/13 3:28 PM
 */
public class ExternalSystemRecentTaskListModel extends DefaultListModel {

  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final Project         myProject;

  public ExternalSystemRecentTaskListModel(@NotNull ProjectSystemId externalSystemId, @NotNull Project project) {
    myExternalSystemId = externalSystemId;
    myProject = project;
    ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  @SuppressWarnings("unchecked")
  public void setTasks(@NotNull List<ExternalTaskExecutionInfo> tasks) {
    clear();
    List<ExternalTaskExecutionInfo> tasksToUse = ContainerUtilRt.newArrayList(tasks);
    for (ExternalTaskExecutionInfo task : tasksToUse) {
      addElement(task);
    }
  }

  @SuppressWarnings("unchecked")
  public void setFirst(@NotNull ExternalTaskExecutionInfo task) {
    insertElementAt(task, 0);
    for (int i = 1; i < size(); i++) {
      if (task.equals(getElementAt(i))) {
        remove(i);
        break;
      }
    }
    ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  @NotNull
  public List<ExternalTaskExecutionInfo> getTasks() {
    List<ExternalTaskExecutionInfo> result = ContainerUtilRt.newArrayList();
    for (int i = 0; i < size(); i++) {
      Object e = getElementAt(i);
      if (e instanceof ExternalTaskExecutionInfo) {
        result.add((ExternalTaskExecutionInfo)e);
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  public void ensureSize(int elementsNumber) {
    int toAdd = elementsNumber - size();
    if (toAdd == 0) {
      return;
    }
    if(toAdd < 0) {
      removeRange(elementsNumber, size() - 1);
    }
    while (--toAdd >= 0) {
      addElement(new MyEmptyDescriptor());
    }
  }

  /**
   * Asks current model to remove all 'recent task info' entries which point to tasks from external project with the given path.
   * 
   * @param externalProjectPath  target external project's path
   */
  public void forgetTasksFrom(@NotNull String externalProjectPath) {
    for (int i = size() - 1; i >= 0; i--) {
      Object e = getElementAt(i);
      if (e instanceof ExternalTaskExecutionInfo) {
        String path = ((ExternalTaskExecutionInfo)e).getSettings().getExternalProjectPath();
        if (externalProjectPath.equals(path)
            || externalProjectPath.equals(ExternalSystemApiUtil.getRootProjectPath(path, myExternalSystemId, myProject)))
        {
          removeElementAt(i);
        }
      }
    }
    ensureSize(ExternalSystemConstants.RECENT_TASKS_NUMBER);
  }

  static class MyEmptyDescriptor {
  }
}
