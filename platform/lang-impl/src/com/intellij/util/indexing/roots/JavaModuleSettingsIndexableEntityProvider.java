// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaModuleSettingsEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class JavaModuleSettingsIndexableEntityProvider implements IndexableEntityProvider.Enforced<JavaModuleSettingsEntity> {

  @Override
  public @NotNull Class<JavaModuleSettingsEntity> getEntityClass() {
    return JavaModuleSettingsEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(ModuleEntity.class, JavaModuleSettingsIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity entity,
                                                                                                @NotNull Project project) {
    if (entity.getLanguageLevelId() != null) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getModule().getSymbolicId());
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity oldEntity,
                                                                                                   @NotNull JavaModuleSettingsEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (!Objects.equals(newEntity.getLanguageLevelId(), oldEntity.getLanguageLevelId())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getModule().getSymbolicId());
    }
    return Collections.emptyList();
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                                @NotNull ModuleEntity newEntity) {
    List<VirtualFileUrl> newRootUrls = ContentRootIndexableEntityProvider.collectRootUrls(newEntity.getContentRoots());
    List<VirtualFileUrl> oldRootUrls = ContentRootIndexableEntityProvider.collectRootUrls(oldEntity.getContentRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getSymbolicId(), newRootUrls, oldRootUrls);
  }
}
