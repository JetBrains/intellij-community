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
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Ensures that all external system sub-projects are correctly represented at the external system tool window.
 * 
 * @author Denis Zhdanov
 * @since 5/15/13 1:02 PM
 */
@Order(ExternalSystemConstants.BUILTIN_TOOL_WINDOW_SERVICE_ORDER)
public class ToolWindowModuleService extends AbstractToolWindowService<ModuleData> {

  @NotNull
  public static final Function<DataNode<ModuleData>, ExternalProjectPojo> MAPPER
    = new Function<DataNode<ModuleData>, ExternalProjectPojo>() {
    @Override
    public ExternalProjectPojo fun(DataNode<ModuleData> node) {
      return ExternalProjectPojo.from(node.getData());
    }
  };

  @NotNull
  @Override
  public Key<ModuleData> getTargetDataKey() {
    return ProjectKeys.MODULE;
  }

  @Override
  protected void processData(@NotNull final Collection<DataNode<ModuleData>> nodes,
                             @NotNull Project project)
  {
    if (nodes.isEmpty()) {
      return;
    }
    ProjectSystemId externalSystemId = nodes.iterator().next().getData().getOwner();
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
    assert manager != null;

    final MultiMap<DataNode<ProjectData>, DataNode<ModuleData>> grouped = ExternalSystemApiUtil.groupBy(nodes, ProjectKeys.PROJECT);
    Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> data = ContainerUtilRt.newHashMap();
    for (Map.Entry<DataNode<ProjectData>, Collection<DataNode<ModuleData>>> entry : grouped.entrySet()) {
      data.put(ExternalProjectPojo.from(entry.getKey().getData()), ContainerUtilRt.map2List(entry.getValue(), MAPPER));
    }

    AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(project);
    Set<String> pathsToForget = detectRenamedProjects(data, settings.getAvailableProjects());
    if (!pathsToForget.isEmpty()) {
      settings.forgetExternalProjects(pathsToForget);
    }
    Map<ExternalProjectPojo,Collection<ExternalProjectPojo>> projects = ContainerUtilRt.newHashMap(settings.getAvailableProjects());
    projects.putAll(data);
    settings.setAvailableProjects(projects);
  }
  
  @NotNull
  private static Set<String> detectRenamedProjects(@NotNull Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> currentInfo,
                                                   @NotNull Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> oldInfo)
  {
    Map<String/* external config path */, String/* project name */> map = ContainerUtilRt.newHashMap();
    for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : currentInfo.entrySet()) {
      map.put(entry.getKey().getPath(), entry.getKey().getName());
      for (ExternalProjectPojo pojo : entry.getValue()) {
        map.put(pojo.getPath(), pojo.getName());
      }
    }

    Set<String> result = ContainerUtilRt.newHashSet();
    for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : oldInfo.entrySet()) {
      String newName = map.get(entry.getKey().getPath());
      if (newName != null && !newName.equals(entry.getKey().getName())) {
        result.add(entry.getKey().getPath());
      }
      for (ExternalProjectPojo pojo : entry.getValue()) {
        newName = map.get(pojo.getPath());
        if (newName != null && !newName.equals(pojo.getName())) {
          result.add(pojo.getPath());
        }
      }
    }
    return result;
  }
}
