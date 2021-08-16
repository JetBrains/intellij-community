// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaModuleSettingsEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

class JavaModuleSettingsIndexableEntityProvider implements IndexableEntityProvider {

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (entity instanceof JavaModuleSettingsEntity) {
      JavaModuleSettingsEntity settingsEntity = (JavaModuleSettingsEntity)entity;
      if (settingsEntity.getLanguageLevelId() != null) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(settingsEntity.getModule(), project);
      }
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull WorkspaceEntity oldEntity,
                                                                                         @NotNull WorkspaceEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (newEntity instanceof JavaModuleSettingsEntity) {
      JavaModuleSettingsEntity newSettingsEntity = (JavaModuleSettingsEntity)newEntity;
      JavaModuleSettingsEntity oldSettingsEntity = (JavaModuleSettingsEntity)oldEntity;
      if (!Objects.equals(newSettingsEntity.getLanguageLevelId(), oldSettingsEntity.getLanguageLevelId())) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(newSettingsEntity.getModule(), project);
      }
    }
    return Collections.emptyList();
  }
}
