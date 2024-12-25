// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

@ApiStatus.Internal
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public final class LibraryDependencyDataService extends AbstractDependencyDataService<LibraryDependencyData, LibraryOrderEntry> {
  private static final Logger LOG = Logger.getInstance(LibraryDependencyDataService.class);

  @Override
  public @NotNull Key<LibraryDependencyData> getTargetDataKey() {
    return ProjectKeys.LIBRARY_DEPENDENCY;
  }

  @Override
  public @NotNull Class<LibraryOrderEntry> getOrderEntryType() {
    return LibraryOrderEntry.class;
  }

  private static class DataToImport {
    // the order of entries are important
    private final @NotNull LinkedHashMap<Set<String>/* library paths */, LibraryDependencyData> moduleLibraries;
    private final @NotNull LinkedHashMap<String/* library name + scope */, LibraryDependencyData> projectLibraries;
    private final boolean hasUnresolvedLibraries;

    private DataToImport(@NotNull LinkedHashMap<Set<String>, LibraryDependencyData> moduleLibraries,
                         @NotNull LinkedHashMap<String, LibraryDependencyData> projectLibraries,
                         boolean hasUnresolvedLibraries) {
      this.moduleLibraries = moduleLibraries;
      this.projectLibraries = projectLibraries;
      this.hasUnresolvedLibraries = hasUnresolvedLibraries;
    }
  }

  @Override
  protected Map<OrderEntry, OrderAware> importData(@NotNull Collection<? extends DataNode<LibraryDependencyData>> nodesToImport,
                                                   @NotNull Module module,
                                                   @NotNull IdeModifiableModelsProvider modelsProvider) {
    // The general idea is to import all external project library dependencies and module libraries which don't present at the
    // ide side yet and remove all project library dependencies and module libraries which present at the ide but not at
    // the given collection.
    // The trick is that we should perform module settings modification inside try/finally block against target root model.
    // That means that we need to prepare all necessary data, obtain a model and modify it as necessary.
    DataToImport toImport = preProcessData(nodesToImport);

    Map<OrderEntry, OrderAware> orderEntryDataMap = new LinkedHashMap<>();
    ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);

    for (OrderEntry entry : modifiableRootModel.getOrderEntries()) {
      LibraryDependencyData processingResult = null;
      if (OrderEntryUtil.isModuleLibraryOrderEntry(entry)) {
        processingResult = importModuleLibraryOrderEntry((LibraryOrderEntry)entry, toImport.moduleLibraries, modifiableRootModel,
                                                         modelsProvider);
      }
      else if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
        processingResult = importLibraryOrderEntry(libraryOrderEntry, toImport.projectLibraries, modifiableRootModel, modelsProvider,
                                                   toImport.hasUnresolvedLibraries);
        if (processingResult != null) {
          libraryOrderEntry.setExported(processingResult.isExported());
          libraryOrderEntry.setScope(processingResult.getScope());
        }
      }
      if (processingResult != null) {
        orderEntryDataMap.put(entry, processingResult);
      }
    }

    // Import missing library dependencies.
    for (LibraryDependencyData dependencyData : toImport.projectLibraries.values()) {
      OrderEntry entry = importMissingLibraryOrderEntry(dependencyData, modifiableRootModel, modelsProvider, module);
      orderEntryDataMap.put(entry, dependencyData);
    }
    for (LibraryDependencyData dependencyData : toImport.moduleLibraries.values()) {
      OrderEntry entry = importMissingModuleLibraryOrderEntry(dependencyData, modifiableRootModel, modelsProvider,
                                                              module);
      orderEntryDataMap.put(entry, dependencyData);
    }
    return orderEntryDataMap;
  }

  private static @Nullable LibraryDependencyData importModuleLibraryOrderEntry(
    @NotNull LibraryOrderEntry entry,
    @NotNull Map<Set<String>/* library paths */, LibraryDependencyData> moduleLibrariesToImport,
    @NotNull ModifiableRootModel modifiableRootModel,
    @NotNull IdeModifiableModelsProvider modelsProvider
  ) {
    Library library = entry.getLibrary();
    VirtualFile[] libraryFiles = library.getFiles(OrderRootType.CLASSES);
    Set<String> moduleLibraryKey = new HashSet<>(libraryFiles.length);
    for (VirtualFile file : libraryFiles) {
      moduleLibraryKey.add(ExternalSystemApiUtil.getLocalFileSystemPath(file) + entry.getScope().name());
    }
    LibraryDependencyData existing = moduleLibrariesToImport.remove(moduleLibraryKey);
    if (existing == null || !StringUtil.equals(StringUtil.nullize(existing.getInternalName()), library.getName())) {
      modifiableRootModel.removeOrderEntry(entry);
    }
    else {
      syncExistingLibraryDependency(modelsProvider, existing, library, modifiableRootModel, entry.getOwnerModule(), entry);
      return existing;
    }
    return null;
  }

  private static @Nullable LibraryDependencyData importLibraryOrderEntry(
    @NotNull LibraryOrderEntry entry,
    @NotNull Map<String/* library name + scope */, LibraryDependencyData> projectLibrariesToImport,
    @NotNull ModifiableRootModel modifiableRootModel,
    @NotNull IdeModifiableModelsProvider modelsProvider,
    boolean hasUnresolvedLibraries
  ) {
    String libraryName = entry.getLibraryName();
    LibraryDependencyData existing = projectLibrariesToImport.remove(libraryName + entry.getScope().name());
    if (existing != null) {
      if (modelsProvider.findModuleByPublication(existing.getTarget()) == null) {
        return existing;
      }
      else {
        modifiableRootModel.removeOrderEntry(entry);
      }
    }
    else if (!hasUnresolvedLibraries) {
      // There is a possible case that a project has been successfully imported from external model and after
      // that network/repo goes down. We don't want to drop existing binary mappings then.
      modifiableRootModel.removeOrderEntry(entry);
    }
    return null;
  }

  private static @NotNull DataToImport preProcessData(@NotNull Collection<? extends DataNode<LibraryDependencyData>> nodesToImport) {
    LinkedHashMap<Set<String>/* library paths */, LibraryDependencyData> moduleLibrariesToImport = new LinkedHashMap<>();
    LinkedHashMap<String/* library name + scope */, LibraryDependencyData> projectLibrariesToImport = new LinkedHashMap<>();

    boolean hasUnresolved = false;
    for (DataNode<LibraryDependencyData> dependencyNode : nodesToImport) {
      LibraryDependencyData dependencyData = dependencyNode.getData();
      LibraryData libraryData = dependencyData.getTarget();
      hasUnresolved |= libraryData.isUnresolved();
      LibraryLevel dependencyDataLevel = dependencyData.getLevel();
      if (LibraryLevel.MODULE == dependencyDataLevel) {
        Set<String> paths = new HashSet<>();
        for (String path : libraryData.getPaths(LibraryPathType.BINARY)) {
          paths.add(ExternalSystemApiUtil.toCanonicalPath(path) + dependencyData.getScope().name());
        }
        moduleLibrariesToImport.put(paths, dependencyData);
      }
      else if (LibraryLevel.PROJECT == dependencyDataLevel) {
        projectLibrariesToImport.put(libraryData.getInternalName() + dependencyData.getScope().name(), dependencyData);
      }
    }
    return new DataToImport(moduleLibrariesToImport, projectLibrariesToImport, hasUnresolved);
  }

  private static @NotNull OrderEntry importMissingModuleLibraryOrderEntry(@NotNull LibraryDependencyData dependencyData,
                                                                          @NotNull ModifiableRootModel moduleRootModel,
                                                                          @NotNull IdeModifiableModelsProvider modelsProvider,
                                                                          @NotNull Module module
  ) {
    final Library moduleLib;
    final LibraryData libraryData = dependencyData.getTarget();
    final String libraryName = libraryData.getInternalName();
    LibraryTable moduleLibraryTable = moduleRootModel.getModuleLibraryTable();
    if (libraryName.isEmpty()) {
      moduleLib = moduleLibraryTable.createLibrary();
    }
    else {
      moduleLib = moduleLibraryTable.createLibrary(libraryName);
    }
    return syncExistingLibraryDependency(modelsProvider, dependencyData, moduleLib, moduleRootModel, module, null);
  }

  private static @NotNull OrderEntry importMissingLibraryOrderEntry(@NotNull LibraryDependencyData dependencyData,
                                                                    @NotNull ModifiableRootModel moduleRootModel,
                                                                    @NotNull IdeModifiableModelsProvider modelsProvider,
                                                                    @NotNull Module module
  ) {
    final LibraryData libraryData = dependencyData.getTarget();
    final String libraryName = libraryData.getInternalName();
    final Library projectLib = modelsProvider.getLibraryByName(libraryName);
    if (projectLib == null) {
      LibraryTable moduleLibraryTable = moduleRootModel.getModuleLibraryTable();
      return syncExistingLibraryDependency(modelsProvider, dependencyData, moduleLibraryTable.createLibrary(libraryName), moduleRootModel,
                                           module, null);
    }
    LibraryOrderEntry orderEntry = moduleRootModel.addLibraryEntry(projectLib);
    setLibraryScope(orderEntry, projectLib, module, dependencyData);
    ModuleOrderEntry substitutionEntry = modelsProvider.trySubstitute(module, orderEntry, libraryData);
    if (substitutionEntry != null) {
      return substitutionEntry;
    }
    else {
      return orderEntry;
    }
  }

  private static void setLibraryScope(@NotNull LibraryOrderEntry orderEntry,
                                      @NotNull Library lib,
                                      @NotNull Module module,
                                      @NotNull LibraryDependencyData dependencyData) {
    orderEntry.setExported(dependencyData.isExported());
    orderEntry.setScope(dependencyData.getScope());
    if (LOG.isTraceEnabled()) {
      LOG.trace(String.format(
        "Configuring library '%s' of module '%s' to be%s exported and have scope %s",
        lib, module.getName(), dependencyData.isExported() ? " not" : "", dependencyData.getScope()
      ));
    }
  }

  private static @NotNull LibraryOrderEntry syncExistingLibraryDependency(@NotNull IdeModifiableModelsProvider modelsProvider,
                                                                          final @NotNull LibraryDependencyData libraryDependencyData,
                                                                          final @NotNull Library library,
                                                                          final @NotNull ModifiableRootModel moduleRootModel,
                                                                          final @NotNull Module module,
                                                                          @Nullable LibraryOrderEntry currentRegisteredLibraryOrderEntry) {
    final Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    final String libraryName = libraryDependencyData.getInternalName();
    final LibraryData libraryDependencyDataTarget = libraryDependencyData.getTarget();
    Map<OrderRootType, Collection<File>> files = LibraryDataService.prepareLibraryFiles(libraryDependencyDataTarget);
    Set<String> excludedPaths = libraryDependencyDataTarget.getPaths(LibraryPathType.EXCLUDED);
    LibraryDataService.registerPaths(libraryDependencyDataTarget.isUnresolved(), files, excludedPaths, libraryModel, libraryName);
    LibraryOrderEntry orderEntry = currentRegisteredLibraryOrderEntry;
    if (orderEntry == null) {
      orderEntry = findLibraryOrderEntry(moduleRootModel, library, libraryDependencyData.getScope());
    }

    assert orderEntry != null;
    setLibraryScope(orderEntry, library, module, libraryDependencyData);
    return orderEntry;
  }

  private static @Nullable LibraryOrderEntry findLibraryOrderEntry(@NotNull ModifiableRootModel moduleRootModel,
                                                                   @NotNull Library library,
                                                                   @NotNull DependencyScope scope) {
    LibraryOrderEntry candidate = null;
    for (OrderEntry orderEntry : moduleRootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry) {
        Library orderEntryLibrary = libraryOrderEntry.getLibrary();
        if (library == orderEntryLibrary) {
          return libraryOrderEntry;
        }
        // LibraryImpl.equals will return true for unrelated module library if it's just created and empty
        if (library.equals(orderEntryLibrary) && (candidate == null || libraryOrderEntry.getScope() == scope)) {
          candidate = libraryOrderEntry;
        }
      }
    }
    return candidate;
  }
}
