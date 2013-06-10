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
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
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
 * @since 4/12/13 6:19 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class LibraryDependencyDataService extends AbstractDependencyDataService<LibraryDependencyData, LibraryOrderEntry> {

  private static final Logger LOG = Logger.getInstance("#" + LibraryDependencyDataService.class.getName());

  @NotNull private final PlatformFacade         myPlatformFacade;
  @NotNull private final ProjectStructureHelper myProjectStructureHelper;
  @NotNull private final ModuleDataService      myModuleManager;
  @NotNull private final LibraryDataService     myLibraryManager;

  public LibraryDependencyDataService(@NotNull PlatformFacade platformFacade,
                                      @NotNull ProjectStructureHelper helper,
                                      @NotNull ModuleDataService moduleManager,
                                      @NotNull LibraryDataService libraryManager)
  {
    myPlatformFacade = platformFacade;
    myProjectStructureHelper = helper;
    myModuleManager = moduleManager;
    myLibraryManager = libraryManager;
  }

  @NotNull
  @Override
  public Key<LibraryDependencyData> getTargetDataKey() {
    return ProjectKeys.LIBRARY_DEPENDENCY;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<LibraryDependencyData>> toImport, @NotNull Project project, boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    Map<DataNode<ModuleData>, List<DataNode<LibraryDependencyData>>> byModule = ExternalSystemApiUtil.groupBy(toImport, MODULE);
    for (Map.Entry<DataNode<ModuleData>, List<DataNode<LibraryDependencyData>>> entry : byModule.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        myModuleManager.importData(Collections.singleton(entry.getKey()), project, true);
        module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
        if (module == null) {
          LOG.warn(String.format(
            "Can't import library dependencies %s. Reason: target module (%s) is not found at the ide and can't be imported",
            entry.getValue(), entry.getKey()
          ));
          continue;
        }
      }
      importData(entry.getValue(), module, synchronous);
    }
  }

  public void importData(@NotNull final Collection<DataNode<LibraryDependencyData>> nodesToImport,
                         @NotNull final Module module,
                         final boolean synchronous)
  {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
        List<DataNode<LibraryData>> librariesToImport = ContainerUtilRt.newArrayList();
        for (DataNode<LibraryDependencyData> dataNode : nodesToImport) {
          final LibraryDependencyData dependencyData = dataNode.getData();
          final Library library = libraryTable.getLibraryByName(dependencyData.getName());
          if (library == null) {
            DataNode<ProjectData> projectNode = dataNode.getDataNode(ProjectKeys.PROJECT);
            if (projectNode != null) {
              DataNode<LibraryData> libraryNode =
                ExternalSystemApiUtil.find(projectNode, ProjectKeys.LIBRARY, new BooleanFunction<DataNode<LibraryData>>() {
                  @Override
                  public boolean fun(DataNode<LibraryData> node) {
                    return node.getData().equals(dependencyData.getTarget());
                  }
                });
              if (libraryNode != null) {
                librariesToImport.add(libraryNode);
              }
            }
          }
        }
        if (!librariesToImport.isEmpty()) {
          myLibraryManager.importData(librariesToImport, module.getProject(), synchronous);
        }

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        Map<String /* library name */, LibraryOrderEntry> toRemove = ContainerUtilRt.newHashMap();
        for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
          if (entry instanceof LibraryOrderEntry) {
            LibraryOrderEntry e = (LibraryOrderEntry)entry;
            String libraryName = e.getLibraryName();
            if (libraryName != null) {
              toRemove.put(libraryName, e);
            }
          }
        }

        for (DataNode<LibraryDependencyData> dependencyNode : nodesToImport) {
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
            final LibraryDependencyData dependencyData = dependencyNode.getData();
            toRemove.remove(dependencyData.getName());
            final Library library = libraryTable.getLibraryByName(dependencyData.getName());
            if (library == null) {
              assert false;
              continue;
            }
            LibraryOrderEntry orderEntry = myProjectStructureHelper.findIdeLibraryDependency(dependencyData.getName(), moduleRootModel);
            if (orderEntry == null) {
              // We need to get the most up-to-date Library object due to our project model restrictions.
              orderEntry = moduleRootModel.addLibraryEntry(library);
              LOG.info(String.format("Adding library dependency '%s' to module '%s'", library.getName(), module.getName()));
            }
            orderEntry.setExported(dependencyData.isExported());
            orderEntry.setScope(dependencyData.getScope());
            LOG.info(String.format(
              "Configuring library dependency '%s' of module '%s' to be%s exported and have scope %s",
              library.getName(), module.getName(), dependencyData.isExported() ? " not" : "", dependencyData.getScope()
            ));
          }
          finally {
            moduleRootModel.commit();
          }
        }

        if (!toRemove.isEmpty()) {
          removeData(toRemove.values(), module, synchronous);
        }
      }
    });
  }
}
