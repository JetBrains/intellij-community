// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
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
  public @NotNull Collection<? extends IndexableIteratorBuilder> getExistingEntityIteratorBuilder(@NotNull ModuleEntity entity,
                                                                                                  @NotNull Project project) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      iterators.addAll(createIteratorBuildersForDependency(dependency));
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleEntity entity,
                                                                                                @NotNull Project project) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    iterators.addAll(IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getSymbolicId()));
    for (ModuleDependencyItem dependency : entity.getDependencies()) {
      iterators.addAll(createIteratorBuildersForDependency(dependency));
    }
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ModuleEntity oldEntity,
                                                                                                   @NotNull ModuleEntity newEntity) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    List<ModuleDependencyItem> newDependencies = newEntity.getDependencies();
    Collection<ModuleDependencyItem> oldDependencies = new HashSet<>(oldEntity.getDependencies());
    for (ModuleDependencyItem dependency : newDependencies) {
      if (!oldDependencies.contains(dependency)) {
        iterators.addAll(createIteratorBuildersForDependency(dependency));
      }
    }
    if (!iterators.isEmpty()) {
      return iterators;
    }
    return Collections.emptyList();
  }

  @NotNull
  private static Collection<? extends IndexableIteratorBuilder> createIteratorBuildersForDependency(@NotNull ModuleDependencyItem dependency) {
    if (dependency instanceof ModuleDependencyItem.SdkDependency) {
      return IndexableIteratorBuilders.INSTANCE.forSdk(((ModuleDependencyItem.SdkDependency)dependency).getSdkName(),
                                                       ((ModuleDependencyItem.SdkDependency)dependency).getSdkType());
    }
    else if (dependency instanceof ModuleDependencyItem.Exportable.LibraryDependency) {
      LibraryId libraryId = ((ModuleDependencyItem.Exportable.LibraryDependency)dependency).getLibrary();
      return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(libraryId, true);
    }
    else if (dependency instanceof ModuleDependencyItem.InheritedSdkDependency) {
      return IndexableIteratorBuilders.INSTANCE.forInheritedSdk();
    }
    return Collections.emptyList();
  }
}
