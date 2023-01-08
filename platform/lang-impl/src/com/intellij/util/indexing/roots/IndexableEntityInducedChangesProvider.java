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
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

  static <E extends WorkspaceEntity> void forEachRelevantProvider(@NotNull E entity,
                                                                  @NotNull Consumer<IndexableEntityInducedChangesProvider<E>> consumer) {
    //noinspection unchecked
    Class<E> entityInterface = (Class<E>)entity.getEntityInterface();
    for (IndexableEntityInducedChangesProvider<? extends WorkspaceEntity> provider : EP_NAME.getExtensionList()) {
      if (entityInterface.equals(provider.getEntityInterface())) {
        //noinspection unchecked
        consumer.accept((IndexableEntityInducedChangesProvider<E>)provider);
      }
    }
  }

  static <E extends WorkspaceEntity> void forEachRelevantProvider(@NotNull EntityChange<? extends E> entityChange,
                                                                  @NotNull BiConsumer<IndexableEntityInducedChangesProvider<E>, EntityChange<E>> consumer) {
    Class<E> entityInterface;
    E newEntity = entityChange.getNewEntity();
    if (newEntity != null) {
      //noinspection unchecked
      entityInterface = (Class<E>)newEntity.getEntityInterface();
    }
    else {
      //noinspection unchecked
      entityInterface = (Class<E>)Objects.requireNonNull(entityChange.getOldEntity()).getEntityInterface();
    }

    for (IndexableEntityInducedChangesProvider<? extends WorkspaceEntity> provider : EP_NAME.getExtensionList()) {
      if (entityInterface.equals(provider.getEntityInterface())) {
        //noinspection unchecked
        consumer.accept((IndexableEntityInducedChangesProvider<E>)provider, (EntityChange<E>)entityChange);
      }
    }
  }
}
