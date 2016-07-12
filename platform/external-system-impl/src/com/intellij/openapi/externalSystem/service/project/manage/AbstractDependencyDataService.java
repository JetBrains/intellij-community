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
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 4/14/13 11:21 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public abstract class AbstractDependencyDataService<E extends AbstractDependencyData<?>, I extends ExportableOrderEntry>
  extends AbstractProjectDataService<E, I> {

  private static final Logger LOG = Logger.getInstance(AbstractDependencyDataService.class.getName());


  @Override
  public void importData(@NotNull Collection<DataNode<E>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty()) {
      return;
    }

    MultiMap<DataNode<ModuleData>, DataNode<E>> byModule = ExternalSystemApiUtil.groupBy(toImport, ModuleData.class);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<E>>> entry : byModule.entrySet()) {
      final DataNode<ModuleData> moduleDataNode = entry.getKey();
      Module module = modelsProvider.findIdeModule(moduleDataNode.getData());
      if (module == null) {
        LOG.warn(String.format(
          "Can't import dependencies %s. Reason: target module (%s) is not found at the ide and can't be imported",
          entry.getValue(), moduleDataNode
        ));
        continue;
      }

      final Map<OrderEntry, OrderAware> moduleDependenciesOrder = importData(entry.getValue(), module, modelsProvider);
      final Map<OrderEntry, OrderAware> orderEntryDataMap = moduleDataNode.getUserData(AbstractModuleDataService.ORDERED_DATA_MAP_KEY);
      if(orderEntryDataMap != null) {
        orderEntryDataMap.putAll(moduleDependenciesOrder);
      } else {
        moduleDataNode.putUserData(AbstractModuleDataService.ORDERED_DATA_MAP_KEY, moduleDependenciesOrder);
      }
    }
  }

  protected abstract Map<OrderEntry, OrderAware> importData(@NotNull Collection<DataNode<E>> nodesToImport,
                                                            @NotNull Module module,
                                                            @NotNull IdeModifiableModelsProvider modelsProvider);

  @NotNull
  @Override
  public Computable<Collection<I>> computeOrphanData(@NotNull final Collection<DataNode<E>> toImport,
                                                     @NotNull final ProjectData projectData,
                                                     @NotNull final Project project,
                                                     @NotNull final IdeModifiableModelsProvider modelsProvider) {
    return () -> {
      MultiMap<String /*module name*/, String /*dep name*/> byModuleName = MultiMap.create();
      for (DataNode<E> node : toImport) {
        final AbstractDependencyData data = node.getData();
        byModuleName.putValue(data.getOwnerModule().getInternalName(), getInternalName(data));
      }

      final ModifiableModuleModel modifiableModuleModel = modelsProvider.getModifiableModuleModel();
      List<I> orphanEntries = ContainerUtil.newSmartList();
      for (Module module : modelsProvider.getModules(projectData)) {
        for (OrderEntry entry : modelsProvider.getOrderEntries(module)) {
          // do not remove recently created library w/o name
          if (entry instanceof LibraryOrderEntry &&
              ((LibraryOrderEntry)entry).getLibraryName() == null &&
              entry.getUrls(OrderRootType.CLASSES).length == 0) {
            continue;
          }
          if (getOrderEntryType().isInstance(entry)) {
            final String moduleName = ObjectUtils.chooseNotNull(modifiableModuleModel.getNewName(entry.getOwnerModule()), entry.getOwnerModule().getName()) ;
            //noinspection unchecked
            if (!byModuleName.get(moduleName).contains(getOrderEntryName(modelsProvider, (I)entry))) {
              //noinspection unchecked
              orphanEntries.add((I)entry);
            }
          }
        }
      }

      return orphanEntries;
    };
  }

  @NotNull
  protected abstract Class<I> getOrderEntryType();

  protected String getOrderEntryName(@NotNull IdeModifiableModelsProvider modelsProvider, @NotNull I orderEntry) {
    return orderEntry.getPresentableName();
  }

  @Override
  public void removeData(@NotNull Computable<Collection<I>> toRemoveComputable,
                         @NotNull Collection<DataNode<E>> toIgnore,
                         @NotNull ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    Map<Module, Collection<ExportableOrderEntry>> byModule = groupByModule(toRemoveComputable.compute());
    for (Map.Entry<Module, Collection<ExportableOrderEntry>> entry : byModule.entrySet()) {
      removeData(entry.getValue(), entry.getKey(), modelsProvider);
    }
  }

  @NotNull
  private static Map<Module, Collection<ExportableOrderEntry>> groupByModule(@NotNull Collection<? extends ExportableOrderEntry> data) {
    Map<Module, Collection<ExportableOrderEntry>> result = ContainerUtilRt.newHashMap();
    for (ExportableOrderEntry entry : data) {
      Collection<ExportableOrderEntry> entries = result.get(entry.getOwnerModule());
      if (entries == null) {
        result.put(entry.getOwnerModule(), entries = ContainerUtilRt.newArrayList());
      }
      entries.add(entry);
    }
    return result;
  }

  protected void removeData(@NotNull Collection<? extends ExportableOrderEntry> toRemove,
                            @NotNull Module module,
                            @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toRemove.isEmpty()) {
      return;
    }
    final ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);
    for (final ExportableOrderEntry dependency : toRemove) {
      modifiableRootModel.removeOrderEntry(dependency);
    }
  }

  private static String getInternalName(final AbstractDependencyData data) {
    if (data instanceof LibraryDependencyData) {
      final String name = data.getInternalName();
      if (StringUtil.isNotEmpty(name)) {
        return name;
      }
      else {
        Set<String> paths = ((LibraryDependencyData)data).getTarget().getPaths(LibraryPathType.BINARY);
        if (!paths.isEmpty()) {
          String url = paths.iterator().next();
          return PathUtil.toPresentableUrl(url);
        }
        else {
          return ProjectBundle.message("library.empty.library.item");
        }
      }
    }
    return data.getInternalName();
  }
}
