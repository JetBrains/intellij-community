// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.ide.VirtualFileUrlManagerUtil;
import com.intellij.workspaceModel.storage.bridgeEntities.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

class ExcludeUrlIndexableEntityProvider implements IndexableEntityProvider<ExcludeUrlEntity> {

  @Override
  public @NotNull Class<ExcludeUrlEntity> getEntityClass() {
    return ExcludeUrlEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ExcludeUrlEntity entity,
                                                                                                @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ExcludeUrlEntity oldEntity,
                                                                                                   @NotNull ExcludeUrlEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (VirtualFileUrlManagerUtil.isEqualOrParentOf(newEntity.getUrl(), oldEntity.getUrl())) return Collections.emptyList();
    return createBuilders(oldEntity);
  }


  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull ExcludeUrlEntity entity,
                                                                                                  @NotNull Project project) {
    return createBuilders(entity);
  }

  @NotNull
  private static Collection<? extends IndexableIteratorBuilder> createBuilders(@NotNull ExcludeUrlEntity entity) {

    ContentRootEntity contentRoot = RootsKt.getContentRoot(entity);
    if (contentRoot != null) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(contentRoot.getModule().getSymbolicId(), entity.getUrl());
    }
    LibraryEntity library = DependenciesKt.getLibrary(entity);
    if (library != null) {
      return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(library.getSymbolicId(), true);
    }
    return Collections.emptyList();
  }
}
