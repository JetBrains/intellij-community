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
package com.intellij.openapi.externalSystem.service.task;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemTasksTreeModel;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 5/15/13 11:46 AM
 */
@Order(ExternalSystemConstants.BUILTIN_TOOL_WINDOW_SERVICE_ORDER)
public class ToolWindowProjectService extends AbstractToolWindowService<ProjectData> {

  @NotNull
  @Override
  public Key<ProjectData> getTargetDataKey() {
    return ProjectKeys.PROJECT;
  }

  @Override
  protected void processData(@NotNull final Collection<DataNode<ProjectData>> nodes, @NotNull final ExternalSystemTasksTreeModel model) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (DataNode<ProjectData> node : nodes) {
          model.ensureProjectNodeExists(node.getData());
        } 
      }
    });
     
  }
}
