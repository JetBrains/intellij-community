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
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;

/**
 * @author Denis Zhdanov
 * @since 4/15/13 8:37 AM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ModuleDependencyDataService extends AbstractDependencyDataService<ModuleDependencyData, ModuleOrderEntry> {

  private static final Logger LOG = Logger.getInstance("#" + ModuleDependencyDataService.class.getName());

  @NotNull
  @Override
  public Key<ModuleDependencyData> getTargetDataKey() {
    return ProjectKeys.MODULE_DEPENDENCY;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ModuleDependencyData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull PlatformFacade platformFacade,
                         boolean synchronous) {
    MultiMap<DataNode<ModuleData>, DataNode<ModuleDependencyData>> byModule = ExternalSystemApiUtil.groupBy(toImport, MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<ModuleDependencyData>>> entry : byModule.entrySet()) {
      Module ideModule = platformFacade.findIdeModule(entry.getKey().getData(), project);
      if (ideModule == null) {
        LOG.warn(String.format(
          "Can't import module dependencies %s. Reason: target module (%s) is not found at the ide and can't be imported",
          entry.getValue(), entry.getKey()
        ));
        continue;
      }
      importData(entry.getValue(), ideModule, platformFacade, synchronous);
    }
  }

  @NotNull
  @Override
  public Class<ModuleOrderEntry> getOrderEntryType() {
    return ModuleOrderEntry.class;
  }

  @Override
  protected String getOrderEntryName(@NotNull ModuleOrderEntry orderEntry) {
    return orderEntry.getModuleName();
  }

  private void importData(@NotNull final Collection<DataNode<ModuleDependencyData>> toImport,
                          @NotNull final Module module,
                          @NotNull final PlatformFacade platformFacade,
                          final boolean synchronous)
  {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(module) {
      @Override
      public void execute() {
        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        Map<Pair<String /* dependency module internal name */, /* dependency module scope */DependencyScope> , ModuleOrderEntry> toRemove = ContainerUtilRt.newHashMap();
        for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
          if (entry instanceof ModuleOrderEntry) {
            ModuleOrderEntry e = (ModuleOrderEntry)entry;
            toRemove.put(Pair.create(e.getModuleName(), e.getScope()), e);
          }
        }

        final ModifiableRootModel moduleRootModel = platformFacade.getModuleModifiableModel(module);
        try {
          for (DataNode<ModuleDependencyData> dependencyNode : toImport) {
            final ModuleDependencyData dependencyData = dependencyNode.getData();
            toRemove.remove(Pair.create(dependencyData.getInternalName(), dependencyData.getScope()));
            final String moduleName = dependencyData.getInternalName();
            Module ideDependencyModule = platformFacade.findIdeModule(moduleName, module.getProject());

            ModuleOrderEntry orderEntry;
            if (module.equals(ideDependencyModule)) {
              // skip recursive module dependency check
              continue;
            } else  {
              if(ideDependencyModule == null) {
                LOG.warn(String.format(
                  "Can't import module dependency for '%s' module. Reason: target module (%s) is not found at the ide",
                  module.getName(), dependencyData
                ));
              }
              orderEntry = platformFacade.findIdeModuleDependency(dependencyData, moduleRootModel);
              if (orderEntry == null) {
                orderEntry = ideDependencyModule == null
                             ? moduleRootModel.addInvalidModuleEntry(moduleName)
                             : moduleRootModel.addModuleOrderEntry(ideDependencyModule);
              }
            }

            orderEntry.setScope(dependencyData.getScope());
            orderEntry.setExported(dependencyData.isExported());
          }
        }
        finally {
          moduleRootModel.commit();
        }

        if (!toRemove.isEmpty()) {
          removeData(toRemove.values(), module, platformFacade, synchronous);
        }
      }
    });
  }
}
