// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.concat;

@ApiStatus.Internal
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public final class ModuleDependencyDataService extends AbstractDependencyDataService<ModuleDependencyData, ModuleOrderEntry> {
  private static final Logger LOG = Logger.getInstance(ModuleDependencyDataService.class);

  @Override
  public @NotNull Key<ModuleDependencyData> getTargetDataKey() {
    return ProjectKeys.MODULE_DEPENDENCY;
  }

  @Override
  public @NotNull Class<ModuleOrderEntry> getOrderEntryType() {
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
                                                   final @NotNull Module module,
                                                   final @NotNull IdeModifiableModelsProvider modelsProvider) {
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
    final ArrayList<ModifiableRootModel.Dependency> dependencyToBeAdded = new ArrayList<>(toImport.size());
    final ArrayList<ModuleDependencyData> dependencyDataToBeAdded = new ArrayList<>(toImport.size());
    for (DataNode<ModuleDependencyData> dependencyNode : toImport) {
      final ModuleDependencyData dependencyData = dependencyNode.getData();

      if (processed.contains(dependencyData)) continue;
      processed.add(dependencyData);

      final ModuleData moduleData = dependencyData.getTarget();
      Module ideDependencyModule = modelsProvider.findIdeModule(moduleData);

      if (module.equals(ideDependencyModule)) {
        // skip recursive module dependency check
        continue;
      }

      if (ideDependencyModule == null) {
        LOG.warn(String.format(
          "Can't import module dependency for '%s' module. Reason: target module (%s) is not found at the ide",
          module.getName(), dependencyData
        ));
        dependencyToBeAdded.add(new ModifiableRootModel.InvalidModuleDependency(
          moduleData.getInternalName(),
          dependencyData.getScope(),
          dependencyData.isExported(),
          dependencyData.isProductionOnTestDependency()
        ));
        dependencyDataToBeAdded.add(dependencyData);
        continue;
      }
      final String targetModuleName = ideDependencyModule.getName();
      ModuleOrderEntry existingOrderEntry = toRemove.remove(Pair.create(targetModuleName, dependencyData.getScope()));
      dependencyData.setInternalName(targetModuleName);

      if (existingOrderEntry != null) {
        // Already existing module, continue to next one
        orderEntryDataMap.put(existingOrderEntry, dependencyData);
        continue;
      }
      dependencyToBeAdded.add(new ModifiableRootModel.ValidModuleDependency(
        ideDependencyModule,
        dependencyData.getScope(),
        dependencyData.isExported(),
        dependencyData.isProductionOnTestDependency()
      ));
      dependencyDataToBeAdded.add(dependencyData);

    }
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    List<OrderEntry> entries = modifiableRootModel.addEntries(dependencyToBeAdded);
    for (int i=0; i < dependencyToBeAdded.size(); i++) {
      orderEntryDataMap.put(entries.get(i), dependencyDataToBeAdded.get(i));
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
