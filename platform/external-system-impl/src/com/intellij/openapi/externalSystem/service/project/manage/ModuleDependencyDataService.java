// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.OrderAware;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.concat;

@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public final class ModuleDependencyDataService extends AbstractDependencyDataService<ModuleDependencyData, ModuleOrderEntry> {
  private static final Logger LOG = Logger.getInstance(ModuleDependencyDataService.class);

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
    if (orderEntryModule != null) {
      moduleName = modelsProvider.getModifiableModuleModel().getActualName(orderEntryModule);
    }
    return moduleName;
  }

  @Override
  protected Map<OrderEntry, OrderAware> importData(final @NotNull Collection<? extends DataNode<ModuleDependencyData>> toImport,
                                                   @NotNull final Module module,
                                                   @NotNull final IdeModifiableModelsProvider modelsProvider) {
    final Map<Pair<String /* dependency module internal name */, /* dependency module scope */DependencyScope>, ModuleOrderEntry> toRemove =
      new HashMap<>();
    final Map<OrderEntry, OrderAware> orderEntryDataMap = new LinkedHashMap<>();
    final List<ModuleOrderEntry> duplicatesToRemove = new ArrayList<>();
    for (OrderEntry entry : modelsProvider.getOrderEntries(module)) {
      if (entry instanceof ModuleOrderEntry e) {
        Pair<String, DependencyScope> key = Pair.create(e.getModuleName(), e.getScope());
        if (toRemove.containsKey(key)) {
          duplicatesToRemove.add(e);
        }
        else {
          toRemove.put(key, e);
        }
      }
    }
    final Set<ModuleDependencyData> processed = new HashSet<>();
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    for (DataNode<ModuleDependencyData> dependencyNode : toImport) {
      final ModuleDependencyData dependencyData = dependencyNode.getData();

      if (processed.contains(dependencyData)) continue;
      processed.add(dependencyData);

      final ModuleData moduleData = dependencyData.getTarget();
      Module ideDependencyModule = modelsProvider.findIdeModule(moduleData);

      if (ideDependencyModule != null) {
        final String targetModuleName = ideDependencyModule.getName();
        toRemove.remove(Pair.create(targetModuleName, dependencyData.getScope()));
        dependencyData.setInternalName(targetModuleName);
      }

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
          orderEntry = ReadAction.compute(() ->
            ideDependencyModule == null
            ? modifiableRootModel.addInvalidModuleEntry(moduleData.getInternalName())
            : modifiableRootModel.addModuleOrderEntry(ideDependencyModule));
        }
      }

      orderEntry.setScope(dependencyData.getScope());
      orderEntry.setExported(dependencyData.isExported());

      orderEntry.setProductionOnTestDependency(dependencyData.isProductionOnTestDependency());

      orderEntryDataMap.put(orderEntry, dependencyData);
    }

    if (!toRemove.isEmpty() || !duplicatesToRemove.isEmpty()) {
      Collection<ModuleOrderEntry> orderEntries = ContainerUtil.toCollection(concat(duplicatesToRemove, toRemove.values()));
      removeData(orderEntries, module, modelsProvider);
    }
    return orderEntryDataMap;
  }


  @Override
  protected void removeData(@NotNull Collection<? extends ExportableOrderEntry> toRemove,
                            @NotNull Module module,
                            @NotNull IdeModifiableModelsProvider modelsProvider) {

    // do not remove 'invalid' module dependencies on unloaded modules
    List<? extends ExportableOrderEntry> filteredList = ContainerUtil.filter(toRemove, o -> {
      if (o instanceof ModuleOrderEntry) {
        String moduleName = ((ModuleOrderEntry)o).getModuleName();
        return ModuleManager.getInstance(module.getProject()).getUnloadedModuleDescription(moduleName) == null;
      }
      return true;
    });
    super.removeData(filteredList, module, modelsProvider);
  }
}
