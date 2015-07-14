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
import com.intellij.openapi.externalSystem.model.project.AbstractDependencyData;
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
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 4/14/13 11:21 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public abstract class AbstractDependencyDataService<E extends AbstractDependencyData<?>, I extends ExportableOrderEntry>
  extends AbstractProjectDataService<E, I>
{

  public void setScope(@NotNull final DependencyScope scope, @NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(dependency.getOwnerModule()) {
      @Override
      public void execute() {
        doForDependency(dependency, new Consumer<ExportableOrderEntry>() {
          @Override
          public void consume(ExportableOrderEntry entry) {
            entry.setScope(scope);
          }
        });
      }
    });
  }

  public void setExported(final boolean exported, @NotNull final ExportableOrderEntry dependency, boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(dependency.getOwnerModule()) {
      @Override
      public void execute() {
        doForDependency(dependency, new Consumer<ExportableOrderEntry>() {
          @Override
          public void consume(ExportableOrderEntry entry) {
            entry.setExported(exported);
          }
        });
      }
    });
  }
  
  private static void doForDependency(@NotNull ExportableOrderEntry entry, @NotNull Consumer<ExportableOrderEntry> consumer) {
    // We need to get an up-to-date modifiable model to work with.
    final ModifiableRootModel moduleRootModel =
      ModifiableModelsProvider.SERVICE.getInstance().getModuleModifiableModel(entry.getOwnerModule());
    try {
      // The thing is that intellij created order entry objects every time new modifiable model is created,
      // that's why we can't use target dependency object as is but need to get a reference to the current
      // entry object from the model instead.
      for (OrderEntry e : moduleRootModel.getOrderEntries()) {
        if (e instanceof ExportableOrderEntry && e.getPresentableName().equals(entry.getPresentableName())) {
          consumer.consume((ExportableOrderEntry)e);
          break;
        }
      }
    }
    finally {
      moduleRootModel.commit();
    }
  }


  @NotNull
  @Override
  public Computable<Collection<I>> computeOrphanData(@NotNull final Collection<DataNode<E>> toImport,
                                                     @NotNull final ProjectData projectData,
                                                     @NotNull final Project project,
                                                     @NotNull final PlatformFacade platformFacade) {
    return new Computable<Collection<I>>() {
      @Override
      public Collection<I> compute() {
        MultiMap<String /*module name*/, String /*dep name*/> byModuleName = MultiMap.create();
        for (DataNode<E> node : toImport) {
          final AbstractDependencyData data = node.getData();
          byModuleName.putValue(data.getOwnerModule().getInternalName(), data.getInternalName());
        }

        List<I> orphanEntries = ContainerUtil.newSmartList();
        for (Module module : platformFacade.getModules(project, projectData)) {
          for (OrderEntry entry : platformFacade.getOrderEntries(module)) {
            if (getOrderEntryType().isInstance(entry) &&
                !byModuleName.get(entry.getOwnerModule().getName()).contains(getOrderEntryName((I)entry))) {
              orphanEntries.add((I)entry);
            }
          }
        }

        return orphanEntries;
      }
    };
  }

  @NotNull
  public abstract Class<I> getOrderEntryType();

  protected String getOrderEntryName(@NotNull I orderEntry) {
    return orderEntry.getPresentableName();
  }

  @Override
  public void removeData(@NotNull final Computable<Collection<I>> toRemoveComputable,
                         @NotNull final Collection<DataNode<E>> toIgnore,
                         @NotNull final ProjectData projectData,
                         @NotNull final Project project,
                         @NotNull final PlatformFacade platformFacade,
                         final boolean synchronous) {
    Map<Module, Collection<ExportableOrderEntry>> byModule = groupByModule(toRemoveComputable.compute());
    for (Map.Entry<Module, Collection<ExportableOrderEntry>> entry : byModule.entrySet()) {
      removeData(entry.getValue(), entry.getKey(), platformFacade, synchronous);
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
                         @NotNull final Module module,
                         @NotNull final PlatformFacade platformFacade,
                         boolean synchronous) {
    if (toRemove.isEmpty()) {
      return;
    }
    for (final ExportableOrderEntry dependency : toRemove) {
      ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(dependency.getOwnerModule()) {
        @Override
        public void execute() {
          final ModifiableRootModel moduleRootModel = platformFacade.getModuleModifiableModel(module);
          try {
            // The thing is that intellij created order entry objects every time new modifiable model is created,
            // that's why we can't use target dependency object as is but need to get a reference to the current
            // entry object from the model instead.
            for (OrderEntry entry : moduleRootModel.getOrderEntries()) {
              if (entry instanceof ExportableOrderEntry) {
                ExportableOrderEntry orderEntry = (ExportableOrderEntry)entry;
                if (orderEntry.getPresentableName().equals(dependency.getPresentableName()) &&
                    orderEntry.getScope().equals(dependency.getScope())) {
                  moduleRootModel.removeOrderEntry(entry);
                  break;
                }
              }
              else if (entry.getPresentableName().equals(dependency.getPresentableName())) {
                moduleRootModel.removeOrderEntry(entry);
                break;
              }
            }
          }
          finally {
            moduleRootModel.commit();
          }
        }
      });
    }
  }
}
