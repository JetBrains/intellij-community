// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaModuleSettingsEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
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
  public @NotNull Collection<? extends IndexableFilesIterator> getExistingEntityIterator(@NotNull JavaModuleSettingsEntity entity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getExistingEntityForModuleIterator(@NotNull JavaModuleSettingsEntity entity,
                                                                                                  @NotNull ModuleEntity moduleEntity,
                                                                                                  @NotNull WorkspaceEntityStorage entityStorage,
                                                                                                  @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull JavaModuleSettingsEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project) {
    if (entity.getLanguageLevelId() != null) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(entity.getModule(), project);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull JavaModuleSettingsEntity oldEntity,
                                                                                         @NotNull JavaModuleSettingsEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    if (!Objects.equals(newEntity.getLanguageLevelId(), oldEntity.getLanguageLevelId())) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity.getModule(), project);
    }
    return Collections.emptyList();
  }
}
