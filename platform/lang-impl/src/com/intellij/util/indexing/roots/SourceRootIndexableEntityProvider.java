// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ExtensionsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class SourceRootIndexableEntityProvider implements IndexableEntityProvider.ModuleEntityDependent<SourceRootEntity>,
                                                   IndexableEntityProvider.Existing<SourceRootEntity> {

  @Override
  public @NotNull Class<SourceRootEntity> getEntityClass() {
    return SourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getPersistentId(), collectRootUrls(ExtensionsKt.getSourceRoots(entity)));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull SourceRootEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getContentRoot().getModule().getPersistentId(), entity.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull SourceRootEntity oldEntity,
                                                                                                   @NotNull SourceRootEntity newEntity) {
    if (!(newEntity.getUrl().equals(oldEntity.getUrl())) || !newEntity.getRootType().equals(oldEntity.getRootType())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getContentRoot().getModule().getPersistentId(), newEntity.getUrl());
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedModuleEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                        @NotNull ModuleEntity newEntity,
                                                                                                        @NotNull Project project) {
    List<VirtualFileUrl> newRoots = collectRootUrls(ExtensionsKt.getSourceRoots(newEntity));
    List<VirtualFileUrl> oldRoots = collectRootUrls(ExtensionsKt.getSourceRoots(oldEntity));
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getPersistentId(), newRoots, oldRoots);
  }

  @NotNull
  private static List<VirtualFileUrl> collectRootUrls(List<SourceRootEntity> newContentRoots) {
    return newContentRoots.stream().map(o -> o.getUrl()).filter(o -> o != null).collect(Collectors.toList());
  }
}
