// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleCustomImlDataEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
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
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleCustomImlDataEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getModule().getPersistentId());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ModuleCustomImlDataEntity oldEntity,
                                                                                                   @NotNull ModuleCustomImlDataEntity newEntity) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedModuleEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                        @NotNull ModuleEntity newEntity,
                                                                                                        @NotNull Project project) {
    if (shouldBeReindexed(newEntity, oldEntity)) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getPersistentId());
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
