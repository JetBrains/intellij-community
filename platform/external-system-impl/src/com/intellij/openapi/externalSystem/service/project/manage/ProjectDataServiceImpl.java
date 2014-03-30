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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 2/21/13 2:40 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ProjectDataServiceImpl implements ProjectDataService<ProjectData, Project> {
  
  @NotNull
  @Override
  public Key<ProjectData> getTargetDataKey() {
    return ProjectKeys.PROJECT;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ProjectData>> toImport, @NotNull Project project, boolean synchronous) {
    if (toImport.size() != 1) {
      throw new IllegalArgumentException(String.format("Expected to get a single project but got %d: %s", toImport.size(), toImport));
    }
    DataNode<ProjectData> node = toImport.iterator().next();
    ProjectData projectData = node.getData();
    
    if (!ExternalSystemApiUtil.isNewProjectConstruction() && !ExternalSystemUtil.isOneToOneMapping(project, node)) {
      return;
    }
    
    if (!project.getName().equals(projectData.getInternalName())) {
      renameProject(projectData.getInternalName(), projectData.getOwner(), project, synchronous);
    }
  }

  @Override
  public void removeData(@NotNull Collection<? extends Project> toRemove, @NotNull Project project, boolean synchronous) {
  }

  @SuppressWarnings("MethodMayBeStatic")
  public void renameProject(@NotNull final String newName,
                            @NotNull final ProjectSystemId externalSystemId,
                            @NotNull final Project project,
                            boolean synchronous)
  {
    if (!(project instanceof ProjectEx) || newName.equals(project.getName())) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        String oldName = project.getName();
        ((ProjectEx)project).setProjectName(newName);
        ExternalSystemApiUtil.getSettings(project, externalSystemId).getPublisher().onProjectRenamed(oldName, newName);
      }
    });
  }

}
