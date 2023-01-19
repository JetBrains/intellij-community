// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleGroupPathEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class ModuleGroupPathIndexableEntityProvider implements IndexableEntityProvider.Enforced<ModuleGroupPathEntity> {

  @Override
  public @NotNull Class<ModuleGroupPathEntity> getEntityClass() {
    return ModuleGroupPathEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(ModuleEntity.class, ModuleGroupPathIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleGroupPathEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getModule().getSymbolicId());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ModuleGroupPathEntity oldEntity,
                                                                                                   @NotNull ModuleGroupPathEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (shouldBeRescanned(oldEntity, newEntity)) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getModule().getSymbolicId());
    }
    return Collections.emptyList();
  }

  private static boolean shouldBeRescanned(@Nullable ModuleGroupPathEntity oldData, @Nullable ModuleGroupPathEntity newData) {
    if ((oldData == null) != (newData == null)) {
      return true;
    }
    if (newData != null && !Objects.equals(newData.getPath(), oldData.getPath())) {
      return true;
    }
    return false;
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                                @NotNull ModuleEntity newEntity) {
    if (shouldBeRescanned(oldEntity.getGroupPath(), newEntity.getGroupPath())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getSymbolicId());
    }
    return Collections.emptyList();
  }
}