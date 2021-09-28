// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class SourceRootIndexableEntityProvider implements IndexableEntityProvider.ModuleEntityDependent<SourceRootEntity>,
                                                   IndexableEntityProvider.Existing<SourceRootEntity> {

  @Override
  public @NotNull Class<SourceRootEntity> getEntityClass() {
    return SourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull WorkspaceEntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.persistentId(), collectRootUrls(entity.getSourceRoots()));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull SourceRootEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getContentRoot().getModule().persistentId(), entity.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull SourceRootEntity oldEntity,
                                                                                                   @NotNull SourceRootEntity newEntity) {
    if (!(newEntity.getUrl().equals(oldEntity.getUrl())) || !newEntity.getRootType().equals(oldEntity.getRootType())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getContentRoot().getModule().persistentId(), newEntity.getUrl());
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedModuleEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                        @NotNull ModuleEntity newEntity,
                                                                                                        @NotNull Project project) {
    List<VirtualFileUrl> newRoots = collectRootUrls(newEntity.getSourceRoots());
    List<VirtualFileUrl> oldRoots = collectRootUrls(oldEntity.getSourceRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.persistentId(), newRoots, oldRoots);
  }

  @NotNull
  private static List<VirtualFileUrl> collectRootUrls(Sequence<SourceRootEntity> newContentRoots) {
    return SequencesKt.toList(SequencesKt.mapNotNull(newContentRoots, root -> root.getUrl()));
  }
}
