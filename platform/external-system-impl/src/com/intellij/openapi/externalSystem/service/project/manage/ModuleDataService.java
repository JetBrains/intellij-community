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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.OrderAware;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Encapsulates functionality of importing external system module to the intellij project.
 *
 * @author Denis Zhdanov
 * @since 2/7/12 2:49 PM
 */
@Order(ExternalSystemConstants.BUILTIN_MODULE_DATA_SERVICE_ORDER)
public class ModuleDataService extends AbstractModuleDataService<ModuleData> {

  @NotNull
  @Override
  public Key<ModuleData> getTargetDataKey() {
    return ProjectKeys.MODULE;
  }

  @NotNull
  @Override
  public Computable<Collection<Module>> computeOrphanData(@NotNull final Collection<DataNode<ModuleData>> toImport,
                                                          @NotNull final ProjectData projectData,
                                                          @NotNull final Project project,
                                                          @NotNull final IdeModifiableModelsProvider modelsProvider) {
    return new Computable<Collection<Module>>() {
      @Override
      public Collection<Module> compute() {
        List<Module> orphanIdeModules = ContainerUtil.newSmartList();

        for (Module module : modelsProvider.getModules()) {
          if (!ExternalSystemApiUtil.isExternalSystemAwareModule(projectData.getOwner(), module)) continue;
          if (ExternalSystemApiUtil.getExternalModuleType(module) != null) continue;

          final String rootProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module);
          if (projectData.getLinkedExternalProjectPath().equals(rootProjectPath)) {
            final String projectPath = ExternalSystemApiUtil.getExternalProjectPath(module);
            final String projectId = ExternalSystemApiUtil.getExternalProjectId(module);

            final DataNode<ModuleData> found = ContainerUtil.find(toImport, new Condition<DataNode<ModuleData>>() {
              @Override
              public boolean value(DataNode<ModuleData> node) {
                final ModuleData moduleData = node.getData();
                return moduleData.getId().equals(projectId) && moduleData.getLinkedExternalProjectPath().equals(projectPath);
              }
            });

            if (found == null || !FileUtil.pathsEqual(module.getModuleFilePath(), found.getData().getModuleFilePath())) {
              orphanIdeModules.add(module);
            }
          }
        }

        return orphanIdeModules;
      }
    };
  }
}
