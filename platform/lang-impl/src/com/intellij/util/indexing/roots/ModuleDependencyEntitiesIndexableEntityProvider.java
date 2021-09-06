// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.util.SmartList;
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.*;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId.GlobalLibraryTableId;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ModuleDependencyEntitiesIndexableEntityProvider implements IndexableEntityProvider.Existing<ModuleEntity> {

  @Override
  public @NotNull Class<ModuleEntity> getEntityClass() {
    return ModuleEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getExistingEntityIterator(@NotNull ModuleEntity entity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    List<IndexableFilesIterator> iterators = new SmartList<>();
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      iterators.addAll(createIteratorsForDependency(project, dependency, storage));
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getIteratorsForExistingModule(@NotNull ModuleEntity entity,
                                                                                             @NotNull WorkspaceEntityStorage entityStorage,
                                                                                             @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull ModuleEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project) {
    List<IndexableFilesIterator> iterators = new SmartList<>();
    iterators.addAll(IndexableEntityProviderMethods.INSTANCE.createIterators(entity, project));
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      iterators.addAll(createIteratorsForDependency(project, dependency, storage));
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull ModuleEntity oldEntity,
                                                                                         @NotNull ModuleEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    List<IndexableFilesIterator> iterators = new SmartList<>();
    List<ModuleDependencyItem> newDependencies = newEntity.getDependencies();
    Collection<ModuleDependencyItem> oldDependencies = new HashSet<>(oldEntity.getDependencies());
    for (ModuleDependencyItem dependency : newDependencies) {
      if (!oldDependencies.contains(dependency)) {
        iterators.addAll(createIteratorsForDependency(project, dependency, storage));
      }
    }
    if (!iterators.isEmpty()) {
      return iterators;
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<? extends IndexableFilesIterator> createIteratorsForDependency(@NotNull Project project,
                                                                                           @NotNull ModuleDependencyItem dependency,
                                                                                           @NotNull WorkspaceEntityStorage storageAfter) {
    if (dependency instanceof ModuleDependencyItem.SdkDependency) {
      Sdk sdk = ModifiableRootModelBridge.findSdk(((ModuleDependencyItem.SdkDependency)dependency).getSdkName(),
                                                  ((ModuleDependencyItem.SdkDependency)dependency).getSdkType());
      if (sdk != null) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(sdk);
      }
    }
    else if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
      LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
      LibraryTableId tableId = libraryId.getTableId();
      if (tableId instanceof GlobalLibraryTableId) {
        return findGlobalLibraryIterators(project, (GlobalLibraryTableId)tableId, libraryId);
      }
      else {
        LibraryEntity libraryEntity = storageAfter.resolve(libraryId);
        if (libraryEntity != null) {
          return LibraryIndexableEntityProvider.createIterators(libraryEntity, project, false);
        }
      }
    }
    else if (dependency instanceof ModuleDependencyItem.InheritedSdkDependency) {
      Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
      if (sdk != null) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(sdk);
      }
    }
    return Collections.emptyList();
  }

  private static Collection<IndexableFilesIterator> findGlobalLibraryIterators(Project project,
                                                                               GlobalLibraryTableId tableId,
                                                                               LibraryId libraryId) {
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(tableId.getLevel(), project);
    if (table != null) {
      Library library = table.getLibraryByName(libraryId.getName());
      if (library != null) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(library, libraryId);
      }
    }
    return Collections.emptyList();
  }
}
