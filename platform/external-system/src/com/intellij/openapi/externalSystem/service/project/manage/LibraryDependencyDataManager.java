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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemProjectKeys;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.externalSystem.model.ExternalSystemProjectKeys.MODULE;

/**
 * @author Denis Zhdanov
 * @since 4/12/13 6:19 PM
 */
public class LibraryDependencyDataManager extends AbstractDependencyDataManager<LibraryDependencyData> {

  private static final Logger LOG = Logger.getInstance("#" + LibraryDependencyDataManager.class.getName());

  @NotNull private final PlatformFacade         myPlatformFacade;
  @NotNull private final ProjectStructureHelper myProjectStructureHelper;
  @NotNull private final ModuleDataManager      myModuleManager;
  @NotNull private final LibraryDataManager     myLibraryManager;

  public LibraryDependencyDataManager(@NotNull PlatformFacade platformFacade,
                                      @NotNull ProjectStructureHelper helper,
                                      @NotNull ModuleDataManager moduleManager,
                                      @NotNull LibraryDataManager libraryManager)
  {
    myPlatformFacade = platformFacade;
    myProjectStructureHelper = helper;
    myModuleManager = moduleManager;
    myLibraryManager = libraryManager;
  }

  @NotNull
  @Override
  public Key<LibraryDependencyData> getTargetDataKey() {
    return ExternalSystemProjectKeys.LIBRARY_DEPENDENCY;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<LibraryDependencyData>> toImport, @NotNull Project project, boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    Map<DataNode<ModuleData>, Collection<DataNode<LibraryDependencyData>>> byModule = ExternalSystemUtil.groupBy(toImport, MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<LibraryDependencyData>>> entry : byModule.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        myModuleManager.importData(Collections.singleton(entry.getKey()), project, true);
        module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
        if (module == null) {
          LOG.warn(String.format(
            "Can't import library dependencies %s. Reason: target module (%s) is not found at the ide",
            entry.getValue(), entry.getKey()
          ));
          continue;
        }
      }
      doImportData(entry.getValue(), entry.getKey().getData().getOwner(), project, module, synchronous);
    }
  }

  private void doImportData(@NotNull final Collection<DataNode<LibraryDependencyData>> nodesToImport,
                            @NotNull ProjectSystemId externalSystemId,
                            @NotNull Project project,
                            @NotNull final Module module,
                            final boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(project, externalSystemId, nodesToImport, synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
        Set<LibraryData> librariesToImport = new HashSet<LibraryData>();
        for (DataNode<LibraryDependencyData> dataNode : nodesToImport) {
          LibraryDependencyData data = dataNode.getData();
          final Library library = libraryTable.getLibraryByName(data.getName());
          if (library == null) {
            librariesToImport.add(data.getTarget());
          }
        }
        if (!librariesToImport.isEmpty()) {
          myLibraryManager.importLibraries(librariesToImport, module.getProject(), synchronous);
        }

        for (DataNode<LibraryDependencyData> dependencyNode : nodesToImport) {
          ProjectStructureHelper helper = ServiceManager.getService(module.getProject(), ProjectStructureHelper.class);
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
            final LibraryDependencyData dependencyData = dependencyNode.getData();
            final Library library = libraryTable.getLibraryByName(dependencyData.getName());
            if (library == null) {
              assert false;
              continue;
            }
            LibraryOrderEntry orderEntry = helper.findIdeLibraryDependency(dependencyData.getName(), moduleRootModel);
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
      }
    });
  }

  @Override
  public void removeData(@NotNull Collection<DataNode<LibraryDependencyData>> toRemove, @NotNull Project project, boolean synchronous) {
    if (toRemove.isEmpty()) {
      return;
    }
    Map<DataNode<ModuleData>, Collection<DataNode<LibraryDependencyData>>> byModule = ExternalSystemUtil.groupBy(toRemove, MODULE);
    for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<LibraryDependencyData>>> entry : byModule.entrySet()) {
      Module module = myProjectStructureHelper.findIdeModule(entry.getKey().getData(), project);
      if (module == null) {
        LOG.warn(String.format(
          "Can't remove library dependencies %s. Reason: target module (%s) is not found at the ide",
          entry.getValue(), entry.getKey()
        ));
        continue;
      }
      List<ExportableOrderEntry> dependencies = ContainerUtilRt.newArrayList();
      for (DataNode<LibraryDependencyData> node : entry.getValue()) {
        LibraryOrderEntry dependency
          = myProjectStructureHelper.findIdeLibraryDependency(module.getName(), node.getData().getName(), project);
        if (dependency != null) {
          LOG.warn(String.format(
            "Can't remove library dependency '%s'. Reason: target module (%s) is not found at the ide",
            node, entry.getKey()
          ));
          continue;
        }
      }
      doRemoveData(dependencies, module, synchronous);
    }
  }
}
