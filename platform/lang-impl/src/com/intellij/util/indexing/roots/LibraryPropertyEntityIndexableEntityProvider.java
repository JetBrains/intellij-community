// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
@ApiStatus.Experimental
public class LibraryPropertyEntityIndexableEntityProvider implements IndexableEntityProvider.Enforced<LibraryPropertiesEntity> {

  @Override
  public @NotNull Class<LibraryPropertiesEntity> getEntityClass() {
    return LibraryPropertiesEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull LibraryPropertiesEntity entity,
                                                                                                @NotNull Project project) {
    //  sure we are interested only in libraries used in project, but in case registered library is downloaded
    // no change in dependencies happen, only Added event on LibraryEntity.
    // For debug see com.intellij.roots.libraries.LibraryTest
    return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(entity.getLibrary().getSymbolicId(), false);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull LibraryPropertiesEntity oldEntity,
                                                                                                   @NotNull LibraryPropertiesEntity newEntity,
                                                                                                   @NotNull Project project) {
    LibraryId oldId = oldEntity.getLibrary().getSymbolicId();
    LibraryId newId = newEntity.getLibrary().getSymbolicId();
    if (oldId == newId) {
      return IndexableIteratorBuilders.INSTANCE.forLibraryEntity(newId, false);
    }
    ArrayList<IndexableIteratorBuilder> builders = new ArrayList<>(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(oldId, false));
    builders.addAll(IndexableIteratorBuilders.INSTANCE.forLibraryEntity(newId, false));
    return builders;
  }
}
