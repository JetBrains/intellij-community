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
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.BooleanFunction;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

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
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(module) {
      @Override
      public void execute() {
        importMissingProjectLibraries(module, nodesToImport, synchronous);
        
        // The general idea is to import all external project library dependencies and module libraries which don't present at the
        // ide side yet and remove all project library dependencies and module libraries which present at the ide but not at
        // the given collection.
        // The trick is that we should perform module settings modification inside try/finally block against target root model.
        // That means that we need to prepare all necessary data, obtain a model and modify it as necessary.
        Map<Set<String>/* library paths */, LibraryDependencyData> moduleLibrariesToImport = ContainerUtilRt.newHashMap();
        Map<String/* library name + scope */, LibraryDependencyData> projectLibrariesToImport = ContainerUtilRt.newHashMap();
        Set<LibraryDependencyData> toImport = ContainerUtilRt.newLinkedHashSet();
        
        boolean hasUnresolved = false;
        for (DataNode<LibraryDependencyData> dependencyNode : nodesToImport) {
          LibraryDependencyData dependencyData = dependencyNode.getData();
          LibraryData libraryData = dependencyData.getTarget();
          hasUnresolved |= libraryData.isUnresolved();
          switch (dependencyData.getLevel()) {
            case MODULE:
              if (!libraryData.isUnresolved()) {
                Set<String> paths = ContainerUtilRt.newHashSet();
                for (String path : libraryData.getPaths(LibraryPathType.BINARY)) {
                  paths.add(ExternalSystemApiUtil.toCanonicalPath(path));
                }
                moduleLibrariesToImport.put(paths, dependencyData);
                toImport.add(dependencyData);
              }
              break;
            case PROJECT:
              projectLibrariesToImport.put(libraryData.getInternalName() + dependencyData.getScope().name(), dependencyData);
              toImport.add(dependencyData);
          }
        }

        ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
        LibraryTable moduleLibraryTable = moduleRootModel.getModuleLibraryTable();
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
        try {
          filterUpToDateAndRemoveObsolete(moduleLibrariesToImport, projectLibrariesToImport, toImport, moduleRootModel, hasUnresolved);

          // Import missing library dependencies.
          if (!toImport.isEmpty()) {
            importMissing(toImport, moduleRootModel, moduleLibraryTable, libraryTable, module);
          }
        }
        finally {
          moduleRootModel.commit();
        }
      }
    });
  }

  private void importMissing(@NotNull Set<LibraryDependencyData> toImport,
                             @NotNull ModifiableRootModel moduleRootModel,
                             @NotNull LibraryTable moduleLibraryTable,
                             @NotNull LibraryTable libraryTable,
                             @NotNull Module module)
  {
    for (LibraryDependencyData dependencyData : toImport) {
      LibraryData libraryData = dependencyData.getTarget();
      String libraryName = libraryData.getInternalName();
      switch (dependencyData.getLevel()) {
        case MODULE:
          @SuppressWarnings("ConstantConditions") Library moduleLib = moduleLibraryTable.createLibrary(libraryName);
          Library.ModifiableModel libModel = moduleLib.getModifiableModel();
          try {
            Map<OrderRootType, Collection<File>> files = myLibraryManager.prepareLibraryFiles(libraryData);
            myLibraryManager.registerPaths(files, libModel, libraryName);
          }
          finally {
            libModel.commit();
          }
          break;
        case PROJECT:
          final Library projectLib = libraryTable.getLibraryByName(libraryName);
          if (projectLib == null) {
            assert false;
            continue;
          }
          LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(projectLib);
          LOG.info(String.format("Adding library dependency '%s' to module '%s'", projectLib.getName(), module.getName()));
          orderEntry.setExported(dependencyData.isExported());
          orderEntry.setScope(dependencyData.getScope());
          LOG.info(String.format(
            "Configuring library dependency '%s' of module '%s' to be%s exported and have scope %s",
            projectLib.getName(), module.getName(), dependencyData.isExported() ? " not" : "", dependencyData.getScope()
          ));
      }
    }
  }

  private static void filterUpToDateAndRemoveObsolete(@NotNull Map<Set<String>, LibraryDependencyData> moduleLibrariesToImport,
                                                      @NotNull Map<String, LibraryDependencyData> projectLibrariesToImport,
                                                      @NotNull Set<LibraryDependencyData> toImport,
                                                      @NotNull ModifiableRootModel moduleRootModel,
                                                      boolean hasUnresolvedLibraries)
  {
    Set<String> moduleLibraryKey = ContainerUtilRt.newHashSet();
    for (OrderEntry entry : moduleRootModel.getOrderEntries()) {
      if (entry instanceof ModuleLibraryOrderEntryImpl) {
        Library library = ((ModuleLibraryOrderEntryImpl)entry).getLibrary();
        if (library == null) {
          LOG.warn("Skipping module-level library entry because it doesn't have backing Library object. Entry: " + entry);
          continue;
        }
        moduleLibraryKey.clear();
        for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
          moduleLibraryKey.add(ExternalSystemApiUtil.getLocalFileSystemPath(file));
        }
        LibraryDependencyData existing = moduleLibrariesToImport.remove(moduleLibraryKey);
        if (existing == null) {
          moduleRootModel.removeOrderEntry(entry);
        }
        else {
          toImport.remove(existing);
        }
      }
      else if (entry instanceof LibraryOrderEntry) {
        final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
        final String libraryName = libraryOrderEntry.getLibraryName();
        final LibraryDependencyData existing = projectLibrariesToImport.remove(libraryName + libraryOrderEntry.getScope().name());
        if (existing != null) {
          toImport.remove(existing);
        }
        else if (!hasUnresolvedLibraries) {
          // There is a possible case that a project has been successfully imported from external model and after
          // that network/repo goes down. We don't want to drop existing binary mappings then.
          moduleRootModel.removeOrderEntry(entry);
        }
      }
    }
  }

  private void importMissingProjectLibraries(@NotNull Module module,
                                             @NotNull Collection<DataNode<LibraryDependencyData>> nodesToImport,
                                             boolean synchronous)
  {
    LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
    List<DataNode<LibraryData>> librariesToImport = ContainerUtilRt.newArrayList();
    for (DataNode<LibraryDependencyData> dataNode : nodesToImport) {
      final LibraryDependencyData dependencyData = dataNode.getData();
      if (dependencyData.getLevel() != LibraryLevel.PROJECT) {
        continue;
      }
      final Library library = libraryTable.getLibraryByName(dependencyData.getInternalName());
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
  }
}
