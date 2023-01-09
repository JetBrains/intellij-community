// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.RootsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ExcludeUrlEntityInducedChangesProvider implements IndexableEntityInducedChangesProvider<ExcludeUrlEntity> {
  @Override
  public @NotNull Class<ExcludeUrlEntity> getEntityInterface() {
    return ExcludeUrlEntity.class;
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromAdded(@NotNull ExcludeUrlEntity entity) {
    return getRootChanges(entity, null);
  }

  @NotNull
  private static List<OriginChange> getRootChanges(@NotNull ExcludeUrlEntity entity, @Nullable EntityStorage storageAfter) {
    ContentRootEntity root = RootsKt.getContentRoot(entity);
    if (root == null) {
      return Collections.emptyList();
    }
    if (storageAfter != null) {
      //need to resolve for a new content root that doesn't have deleted excludeUrlEntity
      root = (ContentRootEntity)root.createReference().resolve(storageAfter);
    }
    if (root == null) {
      return Collections.emptyList();
    }
    List<OriginChange> list = new ArrayList<>();
    list.add(new OriginChange(root, OriginAction.SetOrigin));
    for (SourceRootEntity sourceRoot : root.getSourceRoots()) {
      list.add(new OriginChange(sourceRoot, OriginAction.SetOrigin));
    }
    return list;
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromRemoved(@NotNull ExcludeUrlEntity entity, @NotNull EntityStorage storageAfter) {
    return getRootChanges(entity, storageAfter);
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromReplaced(@NotNull ExcludeUrlEntity oldEntity,
                                                                  @NotNull ExcludeUrlEntity newEntity) {
    ArrayList<OriginChange> list = new ArrayList<>();
    list.addAll(getRootChanges(oldEntity, null));
    list.addAll(getRootChanges(newEntity, null));
    return list;
  }

  @Override
  public @NotNull Collection<OriginChange> getInducedChangesFromRefresh(@NotNull ExcludeUrlEntity entity) {
    return getRootChanges(entity, null);
  }
}
