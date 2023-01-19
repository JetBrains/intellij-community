// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleCustomImlDataEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModuleCustomImlDataFilesIndexableEntityProvider implements IndexableEntityProvider.Enforced<ModuleCustomImlDataEntity> {

  @Override
  public @NotNull Class<ModuleCustomImlDataEntity> getEntityClass() {
    return ModuleCustomImlDataEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(DependencyOnParent.create(
      ModuleEntity.class, ModuleCustomImlDataFilesIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleCustomImlDataEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getModule().getSymbolicId());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ModuleCustomImlDataEntity oldEntity,
                                                                                                   @NotNull ModuleCustomImlDataEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (shouldBeRescanned(oldEntity, newEntity)) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getModule().getSymbolicId());
    }
    return Collections.emptyList();
  }

  private static boolean shouldBeRescanned(@Nullable ModuleCustomImlDataEntity oldData, @Nullable ModuleCustomImlDataEntity newData) {
    if ((oldData == null) != (newData == null)) {
      return true;
    }
    if (newData != null) {
      if (!Objects.equals(newData.getRootManagerTagCustomData(), oldData.getRootManagerTagCustomData())) {
        return true;
      }
      if (!Objects.equals(newData.getCustomModuleOptions(), oldData.getCustomModuleOptions())) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                                @NotNull ModuleEntity newEntity) {
    if (shouldBeRescanned(oldEntity.getCustomImlData(), newEntity.getCustomImlData())) {
      List<IndexableIteratorBuilder> result = new ArrayList<>();
      result.addAll(IndexableIteratorBuilders.INSTANCE.forModuleContent(oldEntity.getSymbolicId()));
      result.addAll(IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getSymbolicId()));
      return result;
    }
    return Collections.emptyList();
  }
}
