// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Experimental
public interface IndexableEntityInducedChangesProvider<E extends WorkspaceEntity> {

  ExtensionPointName<IndexableEntityInducedChangesProvider<? extends WorkspaceEntity>> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableEntityInducedChangesProvider");

  enum OriginAction {SetOrigin, RemoveOrigin}

  record OriginChange(@NotNull WorkspaceEntity entity, @NotNull OriginAction action) {
  }

  @NotNull
  Class<E> getEntityInterface();

  @NotNull
  default Collection<OriginChange> getInducedChanges(@NotNull EntityChange<? extends E> change, @NotNull EntityStorage storageAfter) {
    if (change instanceof EntityChange.Added<?>) {
      return getChangesFromAdded(change.getNewEntity());
    }
    else if (change instanceof EntityChange.Removed<?>) {
      return getChangesFromRemoved(change.getOldEntity(), storageAfter);
    }
    else if (change instanceof EntityChange.Replaced<?>) {
      return getChangesFromReplaced(change.getOldEntity(), change.getNewEntity());
    }
    throw new IllegalStateException("Unexpected change " + change.getClass());
  }

  @NotNull
  default Collection<OriginChange> getChangesFromAdded(@NotNull E entity) {
    return Collections.emptyList();
  }

  @NotNull
  default Collection<OriginChange> getChangesFromRemoved(@NotNull E entity, @NotNull EntityStorage storageAfter) {
    return Collections.emptyList();
  }

  @NotNull
  default Collection<OriginChange> getChangesFromReplaced(@NotNull E oldEntity, @NotNull E newEntity) {
    return Collections.emptyList();
  }

  @NotNull
  Collection<OriginChange> getInducedChangesFromRefresh(@NotNull E entity);
}
