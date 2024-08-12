// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.*;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public final class ModuleDependencyEntitiesIndexableEntityProvider implements IndexableEntityProvider.Enforced<ModuleEntity> {

  @Override
  public @NotNull Class<ModuleEntity> getEntityClass() {
    return ModuleEntity.class;
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
                                                                                                   @NotNull ModuleEntity newEntity,
                                                                                                   @NotNull Project project) {
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

  private static @NotNull Collection<? extends IndexableIteratorBuilder> createIteratorBuildersForDependency(@NotNull ModuleDependencyItem dependency) {
    if (dependency instanceof SdkDependency) {
      return IndexableIteratorBuilders.INSTANCE.forSdk(((SdkDependency)dependency).getSdk().getName(),
                                                       ((SdkDependency)dependency).getSdk().getType());
    }
    else if (dependency instanceof LibraryDependency) {
      LibraryId libraryId = ((LibraryDependency)dependency).getLibrary();
      return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(libraryId, true);
    }
    else if (dependency instanceof InheritedSdkDependency) {
      return IndexableIteratorBuilders.INSTANCE.forInheritedSdk();
    }
    return Collections.emptyList();
  }
}
