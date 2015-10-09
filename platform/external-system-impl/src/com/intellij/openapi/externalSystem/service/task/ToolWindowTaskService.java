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

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemKeymapExtension;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;


/**
 * @author Denis Zhdanov
 * @since 5/15/13 7:21 PM
 */
@Order(ExternalSystemConstants.BUILTIN_TOOL_WINDOW_SERVICE_ORDER)
public class ToolWindowTaskService extends AbstractToolWindowService<TaskData> {

  @NotNull
  public static final Function<DataNode<TaskData>, ExternalTaskPojo> MAPPER = new Function<DataNode<TaskData>, ExternalTaskPojo>() {
    @Override
    public ExternalTaskPojo fun(DataNode<TaskData> node) {
      return ExternalTaskPojo.from(node.getData());
    }
  };

  public static final NullableFunction<DataNode<TaskData>, ExternalConfigPathAware> TASK_HOLDER_RETRIEVAL_STRATEGY =
    new NullableFunction<DataNode<TaskData>, ExternalConfigPathAware>() {
      @Nullable
      @Override
      public ExternalConfigPathAware fun(DataNode<TaskData> node) {
        ModuleData moduleData = node.getData(ProjectKeys.MODULE);
        return moduleData == null ? node.getData(ProjectKeys.PROJECT) : moduleData;
      }
    };

  @NotNull
  @Override
  public Key<TaskData> getTargetDataKey() {
    return ProjectKeys.TASK;
  }

  @Override
  protected void processData(@NotNull Collection<DataNode<TaskData>> nodes,
                             @NotNull Project project)
  {
    if (nodes.isEmpty()) {
      return;
    }
    ProjectSystemId externalSystemId = nodes.iterator().next().getData().getOwner();
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;

    ExternalSystemKeymapExtension.updateActions(project, nodes);

    MultiMap<ExternalConfigPathAware, DataNode<TaskData>> grouped = ContainerUtil.groupBy(nodes, TASK_HOLDER_RETRIEVAL_STRATEGY);
    Map<String, Collection<ExternalTaskPojo>> data = ContainerUtilRt.newHashMap();
    for (Map.Entry<ExternalConfigPathAware, Collection<DataNode<TaskData>>> entry : grouped.entrySet()) {
      data.put(entry.getKey().getLinkedExternalProjectPath(), ContainerUtilRt.map2List(entry.getValue(), MAPPER));
    }

    AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(project);
    Map<String, Collection<ExternalTaskPojo>> availableTasks = ContainerUtilRt.newHashMap(settings.getAvailableTasks());
    availableTasks.putAll(data);
    settings.setAvailableTasks(availableTasks);
  }
}
