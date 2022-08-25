// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.bridgeEntities.api.JavaModuleSettingsEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

class JavaModuleSettingsIndexableEntityProvider implements IndexableEntityProvider<JavaModuleSettingsEntity> {

  @Override
  public @NotNull Class<JavaModuleSettingsEntity> getEntityClass() {
    return JavaModuleSettingsEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity entity,
                                                                                                @NotNull Project project) {
    if (entity.getLanguageLevelId() != null) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getModule().getPersistentId());
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity oldEntity,
                                                                                                   @NotNull JavaModuleSettingsEntity newEntity) {
    if (!Objects.equals(newEntity.getLanguageLevelId(), oldEntity.getLanguageLevelId())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getModule().getPersistentId());
    }
    return Collections.emptyList();
  }
}
