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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExportableOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 8:37 AM
 */
public class ModuleDependencyDataService extends AbstractDependencyDataService<ModuleDependencyData> {

  private static final Logger LOG = Logger.getInstance("#" + ModuleDependencyDataService.class.getName());

  @NotNull private final ProjectStructureHelper myProjectStructureHelper;
  @NotNull private final ModuleDataService      myModuleDataManager;

  public ModuleDependencyDataService(@NotNull ProjectStructureHelper projectStructureHelper, @NotNull ModuleDataService manager) {
    myProjectStructureHelper = projectStructureHelper;
    myModuleDataManager = manager;
  }

  @NotNull
  @Override
  public Key<ModuleDependencyData> getTargetDataKey() {
    return ProjectKeys.MODULE_DEPENDENCY;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ModuleDependencyData>> toImport, @NotNull Project project, boolean synchronous) {
    Map<DataNode<ModuleData>, Collection<DataNode<ModuleDependencyData>>> byModule
      = ExternalSystemUtil.groupBy(toImport, ProjectKeys.MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ModuleDependencyData>>> entry : byModule.entrySet()) {
      Module ideModule = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (ideModule == null) {
        myModuleDataManager.importData(Collections.singleton(entry.getKey()), project, true);
        ideModule = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      }
      if (ideModule == null) {
        LOG.warn(String.format(
          "Can't import module dependencies %s. Reason: target module (%s) is not found at the ide and can't be imported",
          entry.getValue(), entry.getKey()
        ));
        continue;
      }
      importData(entry.getValue(), entry.getKey().getData().getOwner(), ideModule, synchronous);
    }
  }

  public void importData(@NotNull final Collection<DataNode<ModuleDependencyData>> toImport,
                         @NotNull ProjectSystemId externalSystemId,
                         @NotNull final Module module,
                         final boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(module.getProject(), externalSystemId, toImport, synchronous, new Runnable() {
      @Override
      public void run() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        try {
          for (DataNode<ModuleDependencyData> dependencyNode : toImport) {
            final ModuleDependencyData dependencyData = dependencyNode.getData();
            final String moduleName = dependencyData.getName();
            Module ideDependencyModule = myProjectStructureHelper.findIdeModule(moduleName, module.getProject());
            if (ideDependencyModule == null) {
              DataNode<ProjectData> projectNode = dependencyNode.getDataNode(ProjectKeys.PROJECT);
              if (projectNode != null) {
                DataNode<ModuleData> n
                  = ExternalSystemUtil.find(projectNode, ProjectKeys.MODULE, new BooleanFunction<DataNode<ModuleData>>() {
                  @Override
                  public boolean fun(DataNode<ModuleData> node) {
                    return node.getData().equals(dependencyData.getTarget());
                  }
                });
                if (n != null) {
                  myModuleDataManager.importData(Collections.singleton(n), module.getProject(), true);
                  ideDependencyModule = myProjectStructureHelper.findIdeModule(moduleName, module.getProject());
                }
              }
            }

            if (ideDependencyModule == null) {
              assert false;
              return;
            }
            else if (ideDependencyModule.equals(module)) {
              // Gradle api returns recursive module dependencies (a module depends on itself) for 'gradle' project.
              continue;
            }

            ModuleOrderEntry orderEntry = myProjectStructureHelper.findIdeModuleDependency(dependencyData, moduleRootModel);
            if (orderEntry == null) {
              orderEntry = moduleRootModel.addModuleOrderEntry(ideDependencyModule);
            }
            orderEntry.setScope(dependencyData.getScope());
            orderEntry.setExported(dependencyData.isExported());
          }
        }
        finally {
          moduleRootModel.commit();
        }
      }
    });
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<ModuleDependencyData>> toRemove, @NotNull Project project, boolean synchronous) {
    if (toRemove.isEmpty()) {
      return;
    }
    Map<DataNode<ModuleData>, Collection<DataNode<ModuleDependencyData>>> byModule = ExternalSystemUtil.groupBy(toRemove, MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ModuleDependencyData>>> entry : byModule.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        continue;
      }
      List<ExportableOrderEntry> dependencies = ContainerUtilRt.newArrayList();
      for (DataNode<ModuleDependencyData> node : entry.getValue()) {
        ModuleOrderEntry dependency = myProjectStructureHelper.findIdeModuleDependency(module.getName(), node.getData().getName(), project);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
      removeData(dependencies, module, synchronous);
    } 
  }
}
