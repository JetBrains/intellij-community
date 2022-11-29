// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ExtensionsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class SourceRootIndexableEntityProvider implements IndexableEntityProvider.ParentEntityDependent<SourceRootEntity, ContentRootEntity>,
                                                   IndexableEntityProvider.Existing<SourceRootEntity> {

  @Override
  public @NotNull Class<SourceRootEntity> getEntityClass() {
    return SourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getSymbolicId(),
                                                             collectRootUrls(ExtensionsKt.getSourceRoots(entity)));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull SourceRootEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getContentRoot().getModule().getSymbolicId(), entity.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull SourceRootEntity oldEntity,
                                                                                                   @NotNull SourceRootEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (!(newEntity.getUrl().equals(oldEntity.getUrl())) || !newEntity.getRootType().equals(oldEntity.getRootType())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getContentRoot().getModule().getSymbolicId(),
                                                               newEntity.getUrl());
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<VirtualFileUrl> collectRootUrls(List<? extends SourceRootEntity> newContentRoots) {
    return ContainerUtil.map(newContentRoots, o -> o.getUrl());
  }

  @Override
  public @NotNull Class<ContentRootEntity> getParentEntityClass() {
    return ContentRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ContentRootEntity oldEntity,
                                                                                                        @NotNull ContentRootEntity newEntity,
                                                                                                        @NotNull Project project) {
    List<VirtualFileUrl> newRoots = collectRootUrls(newEntity.getSourceRoots());
    List<VirtualFileUrl> oldRoots = collectRootUrls(oldEntity.getSourceRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getSymbolicId(), newRoots, oldRoots);
  }
}
