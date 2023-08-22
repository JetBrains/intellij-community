// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.ExtensionsKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.SourceRootEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class SourceRootIndexableEntityProvider implements IndexableEntityProvider.Existing<SourceRootEntity> {

  @Override
  public @NotNull Class<SourceRootEntity> getEntityClass() {
    return SourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(ContentRootEntity.class, SourceRootIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
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

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ContentRootEntity oldEntity,
                                                                                                                @NotNull ContentRootEntity newEntity) {
    List<VirtualFileUrl> newRoots = collectRootUrls(newEntity.getSourceRoots());
    List<VirtualFileUrl> oldRoots = collectRootUrls(oldEntity.getSourceRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getSymbolicId(), newRoots, oldRoots);
  }
}
