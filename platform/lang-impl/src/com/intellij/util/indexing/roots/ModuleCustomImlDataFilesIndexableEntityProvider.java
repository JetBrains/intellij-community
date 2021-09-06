// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleCustomImlDataEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class ModuleCustomImlDataFilesIndexableEntityProvider
  implements IndexableEntityProvider.ModuleEntityDependent<ModuleCustomImlDataEntity> {

  @Override
  public @NotNull Class<ModuleCustomImlDataEntity> getEntityClass() {
    return ModuleCustomImlDataEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull ModuleCustomImlDataEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project) {
    return IndexableEntityProviderMethods.INSTANCE.createIterators(entity.getModule(), project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull ModuleCustomImlDataEntity oldEntity,
                                                                                         @NotNull ModuleCustomImlDataEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedModuleEntityIterator(@NotNull ModuleEntity oldEntity,
                                                                                               @NotNull ModuleEntity newEntity,
                                                                                               @NotNull WorkspaceEntityStorage storage,
                                                                                               @NotNull Project project) {
    if (shouldBeReindexed(newEntity, oldEntity)) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity, project);
    }
    return Collections.emptyList();
  }

  private static boolean shouldBeReindexed(@NotNull ModuleEntity newEntity, @NotNull ModuleEntity oldEntity) {
    ModuleCustomImlDataEntity newCustomData = newEntity.getCustomImlData();
    ModuleCustomImlDataEntity oldCustomData = oldEntity.getCustomImlData();
    if ((oldCustomData == null) != (newCustomData == null)) {
      return true;
    }
    if (newCustomData != null) {
      if (!Objects.equals(newCustomData.getRootManagerTagCustomData(), oldCustomData.getRootManagerTagCustomData())) {
        return true;
      }
      if (!Objects.equals(newCustomData.getCustomModuleOptions(), oldCustomData.getCustomModuleOptions())) {
        return true;
      }
    }
    return false;
  }
}
