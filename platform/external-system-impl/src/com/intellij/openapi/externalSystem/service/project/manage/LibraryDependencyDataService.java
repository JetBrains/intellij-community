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
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.ModuleLibraryOrderEntryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.MODULE;

/**
 * @author Denis Zhdanov
 * @since 4/12/13 6:19 PM
 */
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class LibraryDependencyDataService extends AbstractDependencyDataService<LibraryDependencyData, LibraryOrderEntry> {

  private static final Logger LOG = Logger.getInstance("#" + LibraryDependencyDataService.class.getName());

  @NotNull private final LibraryDataService myLibraryManager;

  public LibraryDependencyDataService(@NotNull LibraryDataService libraryManager) {
    myLibraryManager = libraryManager;
  }

  @NotNull
  @Override
  public Key<LibraryDependencyData> getTargetDataKey() {
    return ProjectKeys.LIBRARY_DEPENDENCY;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<LibraryDependencyData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull PlatformFacade platformFacade,
                         boolean synchronous) {
    if (toImport.isEmpty()) {
      return;
    }

    MyImporter importer = new MyImporter(platformFacade);
    try {
      MultiMap<DataNode<ModuleData>, DataNode<LibraryDependencyData>> byModule = ExternalSystemApiUtil.groupBy(toImport, MODULE);
      for (Map.Entry<DataNode<ModuleData>, Collection<DataNode<LibraryDependencyData>>> entry : byModule.entrySet()) {
        Module module = platformFacade.findIdeModule(entry.getKey().getData(), project);
        Collection<DataNode<LibraryDependencyData>> libraryDependency = entry.getValue();
        if (module == null) {
          LOG.warn(String.format(
            "Can't import library dependencies %s. Reason: target module (%s) is not found at the ide and can't be imported",
            libraryDependency, entry.getKey()
          ));
          continue;
        }
        importer.importData(module, libraryDependency);
      }
      // change libraries first
      ExternalSystemApiUtil.commitChangedModels(synchronous, project, importer.getLibraryModels());
      ExternalSystemApiUtil.commitModels(synchronous, project, importer.getModels());
    }
    catch (Throwable t) {
      ExternalSystemApiUtil.disposeModels(importer.getModels());
      ExceptionUtil.rethrowUnchecked(t);
    }
  }

  @NotNull
  @Override
  public Class<LibraryOrderEntry> getOrderEntryType() {
    return LibraryOrderEntry.class;
  }

  @Override
  protected String getOrderEntryName(@NotNull LibraryOrderEntry orderEntry) {
    return orderEntry.getLibraryName();
  }

  private class MyImporter {
    private final PlatformFacade myPlatformFacade;
    private final List<ModifiableRootModel> myModels = ContainerUtilRt.newArrayList();
    private final List<Library.ModifiableModel> myLibraryModels = ContainerUtilRt.newArrayList();

    private MyImporter(PlatformFacade platformFacade) {
      myPlatformFacade = platformFacade;
    }

    public List<ModifiableRootModel> getModels() {
      return ContainerUtil.newUnmodifiableList(myModels);
    }

    public List<Library.ModifiableModel> getLibraryModels() {
      return ContainerUtil.newUnmodifiableList(myLibraryModels);
    }

    public void importData(@NotNull Module module, @NotNull Collection<DataNode<LibraryDependencyData>> nodesToImport) {
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
                paths.add(ExternalSystemApiUtil.toCanonicalPath(path) + dependencyData.getScope().name());
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

      ModifiableRootModel moduleRootModel = myPlatformFacade.getModuleModifiableModel(module);
      LibraryTable moduleLibraryTable = moduleRootModel.getModuleLibraryTable();
      LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
      syncExistingAndRemoveObsolete(moduleLibrariesToImport, projectLibrariesToImport, toImport, moduleRootModel, hasUnresolved);

      // Import missing library dependencies.
      if (!toImport.isEmpty()) {
        importMissing(toImport, moduleRootModel, moduleLibraryTable, libraryTable, module);
      }
      myModels.add(moduleRootModel);
    }

    private void importMissing(@NotNull Set<LibraryDependencyData> toImport,
                               @NotNull ModifiableRootModel moduleRootModel,
                               @NotNull LibraryTable moduleLibraryTable,
                               @NotNull LibraryTable libraryTable,
                               @NotNull Module module) {
      for (LibraryDependencyData dependencyData : toImport) {
        LibraryData libraryData = dependencyData.getTarget();
        String libraryName = libraryData.getInternalName();
        switch (dependencyData.getLevel()) {
          case MODULE:
            Library moduleLib = moduleLibraryTable.createLibrary(libraryName);
            syncExistingLibraryDependency(dependencyData, moduleLib, moduleRootModel, module);
            break;
          case PROJECT:
            Library projectLib = libraryTable.getLibraryByName(libraryName);
            if (projectLib == null) {
              syncExistingLibraryDependency(dependencyData, moduleLibraryTable.createLibrary(libraryName), moduleRootModel, module);
              break;
            }
            LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(projectLib);
            setLibraryScope(orderEntry, projectLib, module, dependencyData);
        }
      }
    }

    private void setLibraryScope(@NotNull LibraryOrderEntry orderEntry,
                                        @NotNull Library lib,
                                        @NotNull Module module,
                                        @NotNull LibraryDependencyData dependencyData) {
      LOG.debug(String.format("Adding library dependency '%s' to module '%s'", lib.getName(), module.getName()));
      orderEntry.setExported(dependencyData.isExported());
      orderEntry.setScope(dependencyData.getScope());
      LOG.debug(String.format("Configuring library dependency '%s' of module '%s' to be%s exported and have scope %s", lib.getName(), module.getName(), dependencyData.isExported() ? " not" : "", dependencyData.getScope()));
    }

    private void syncExistingAndRemoveObsolete(@NotNull Map<Set<String>, LibraryDependencyData> moduleLibrariesToImport,
                                               @NotNull Map<String, LibraryDependencyData> projectLibrariesToImport,
                                               @NotNull Set<LibraryDependencyData> toImport,
                                               @NotNull ModifiableRootModel moduleRootModel,
                                               boolean hasUnresolvedLibraries) {
      Set<String> moduleLibraryKey = ContainerUtilRt.newHashSet();
      for (OrderEntry entry : moduleRootModel.getOrderEntries()) {
        if (entry instanceof ModuleLibraryOrderEntryImpl) {
          ModuleLibraryOrderEntryImpl moduleLibraryOrderEntry = (ModuleLibraryOrderEntryImpl)entry;
          Library library = moduleLibraryOrderEntry.getLibrary();
          if (library == null) {
            LOG.warn("Skipping module-level library entry because it doesn't have backing Library object. Entry: " + entry);
            continue;
          }
          moduleLibraryKey.clear();
          for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
            moduleLibraryKey.add(ExternalSystemApiUtil.getLocalFileSystemPath(file) + moduleLibraryOrderEntry.getScope().name());
          }
          LibraryDependencyData existing = moduleLibrariesToImport.remove(moduleLibraryKey);
          if (existing == null) {
            moduleRootModel.removeOrderEntry(entry);
          }
          else {
            syncExistingLibraryDependency(existing, library, moduleRootModel, moduleLibraryOrderEntry.getOwnerModule());
            toImport.remove(existing);
          }
        }
        else if (entry instanceof LibraryOrderEntry) {
          LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
          String libraryName = libraryOrderEntry.getLibraryName();
          LibraryDependencyData existing = projectLibrariesToImport.remove(libraryName + libraryOrderEntry.getScope().name());
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

    private void syncExistingLibraryDependency(@NotNull LibraryDependencyData libraryDependencyData,
                                               @NotNull Library library,
                                               @NotNull ModifiableRootModel moduleRootModel,
                                               @NotNull Module module) {
      Library.ModifiableModel libModel = library.getModifiableModel();
      String libraryName = libraryDependencyData.getInternalName();
      Map<OrderRootType, Collection<File>> files = myLibraryManager.prepareLibraryFiles(libraryDependencyData.getTarget());
      myLibraryManager.registerPaths(files, libModel, libraryName);
      LibraryOrderEntry orderEntry = moduleRootModel.findLibraryOrderEntry(library);
      assert orderEntry != null;
      setLibraryScope(orderEntry, library, module, libraryDependencyData);
      myLibraryModels.add(libModel);
    }
  }
}
