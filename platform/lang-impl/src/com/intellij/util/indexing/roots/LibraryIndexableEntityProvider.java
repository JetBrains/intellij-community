// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

@ApiStatus.Internal
@ApiStatus.Experimental
class LibraryIndexableEntityProvider implements IndexableEntityProvider<LibraryEntity> {

  @Override
  public @NotNull Class<LibraryEntity> getEntityClass() {
    return LibraryEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull LibraryEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(entity.persistentId(), false);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull LibraryEntity oldEntity,
                                                                                                   @NotNull LibraryEntity newEntity) {
    return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(newEntity.persistentId(), false);
  }
}
