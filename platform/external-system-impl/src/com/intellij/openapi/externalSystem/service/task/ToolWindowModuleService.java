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
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemTasksTreeModel;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Ensures that all external system sub-projects are correctly represented at the external system tool window.
 * 
 * @author Denis Zhdanov
 * @since 5/15/13 1:02 PM
 */
@Order(ExternalSystemConstants.BUILTIN_TOOL_WINDOW_SERVICE_ORDER)
public class ToolWindowModuleService extends AbstractToolWindowService<ModuleData> {

  @NotNull
  public static final Function<DataNode<ModuleData>, ModuleData> MAPPER = new Function<DataNode<ModuleData>, ModuleData>() {
    @Override
    public ModuleData fun(DataNode<ModuleData> node) {
      return node.getData();
    }
  };

  @NotNull
  @Override
  public Key<ModuleData> getTargetDataKey() {
    return ProjectKeys.MODULE;
  }

  @Override
  protected void processData(@NotNull final Collection<DataNode<ModuleData>> nodes, @NotNull final ExternalSystemTasksTreeModel model) {
    final Map<DataNode<ProjectData>, List<DataNode<ModuleData>>> grouped = ExternalSystemApiUtil.groupBy(nodes, ProjectKeys.PROJECT);
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        for (Map.Entry<DataNode<ProjectData>, List<DataNode<ModuleData>>> entry : grouped.entrySet()) {
          model.ensureSubProjectsStructure(entry.getKey().getData(), ContainerUtilRt.map2List(entry.getValue(), MAPPER));
        } 
      }
    });
  }
}
