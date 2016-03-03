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
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleOrderEntryImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

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

  @NotNull
  @Override
  public Class<ModuleOrderEntry> getOrderEntryType() {
    return ModuleOrderEntry.class;
  }

  @Override
  protected String getOrderEntryName(@NotNull IdeModifiableModelsProvider modelsProvider, @NotNull ModuleOrderEntry orderEntry) {
    String moduleName = orderEntry.getModuleName();
    final Module orderEntryModule = orderEntry.getModule();
    if(orderEntryModule != null) {
      final String newName = modelsProvider.getModifiableModuleModel().getNewName(orderEntryModule);
      if (newName != null) {
        moduleName = newName;
      }
    }
    return moduleName;
  }

  @Override
  protected Map<OrderEntry, OrderAware> importData(@NotNull final Collection<DataNode<ModuleDependencyData>> toImport,
                                                 @NotNull final Module module,
                                                 @NotNull final IdeModifiableModelsProvider modelsProvider) {
    final Map<Pair<String /* dependency module internal name */, /* dependency module scope */DependencyScope>, ModuleOrderEntry> toRemove =
      ContainerUtilRt.newHashMap();
    final Map<OrderEntry, OrderAware> orderEntryDataMap = ContainerUtil.newLinkedHashMap();

    for (OrderEntry entry : modelsProvider.getOrderEntries(module)) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry e = (ModuleOrderEntry)entry;
        toRemove.put(Pair.create(e.getModuleName(), e.getScope()), e);
      }
    }
    final Set<ModuleDependencyData> processed = ContainerUtil.newHashSet();
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    for (DataNode<ModuleDependencyData> dependencyNode : toImport) {
      final ModuleDependencyData dependencyData = dependencyNode.getData();

      if (processed.contains(dependencyData)) continue;
      processed.add(dependencyData);

      toRemove.remove(Pair.create(dependencyData.getInternalName(), dependencyData.getScope()));
      final String moduleName = dependencyData.getInternalName();
      Module ideDependencyModule = modelsProvider.findIdeModule(moduleName);

      ModuleOrderEntry orderEntry;
      if (module.equals(ideDependencyModule)) {
        // skip recursive module dependency check
        continue;
      }
      else {
        if (ideDependencyModule == null) {
          LOG.warn(String.format(
            "Can't import module dependency for '%s' module. Reason: target module (%s) is not found at the ide",
            module.getName(), dependencyData
          ));
        }
        orderEntry = modelsProvider.findIdeModuleDependency(dependencyData, module);
        if (orderEntry == null) {
          orderEntry = ideDependencyModule == null
                       ? modifiableRootModel.addInvalidModuleEntry(moduleName)
                       : modifiableRootModel.addModuleOrderEntry(ideDependencyModule);
        }
      }

      orderEntry.setScope(dependencyData.getScope());
      orderEntry.setExported(dependencyData.isExported());

      final boolean productionOnTestDependency = dependencyData.isProductionOnTestDependency();
      if (orderEntry instanceof ModuleOrderEntryImpl) {
        ((ModuleOrderEntryImpl)orderEntry).setProductionOnTestDependency(productionOnTestDependency);
      }
      else if (productionOnTestDependency) {
        LOG.warn("Unable to set productionOnTestDependency for entry: " + orderEntry);
      }

      orderEntryDataMap.put(orderEntry, dependencyData);
    }

    if (!toRemove.isEmpty()) {
      removeData(toRemove.values(), module, modelsProvider);
    }

    return orderEntryDataMap;
  }
}
