// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleCustomImlDataEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class ModuleCustomImlDataFilesIndexableEntityProvider implements IndexableEntityProvider {

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (entity instanceof ModuleCustomImlDataEntity) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(((ModuleCustomImlDataEntity)entity).getModule(), project);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull WorkspaceEntity oldEntity,
                                                                                         @NotNull WorkspaceEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (newEntity instanceof ModuleEntity) {
      ModuleEntity oldModuleEntity = (ModuleEntity)oldEntity;
      ModuleEntity newModuleEntity = (ModuleEntity)newEntity;
      if (shouldBeReindexed(newModuleEntity, oldModuleEntity)) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(newModuleEntity, project);
      }
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
