// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ExcludeUrlEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.RootsKt;
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ExcludeUrlEntityInducedChangesProvider implements IndexableEntityInducedChangesProvider<ExcludeUrlEntity> {
  @Override
  public @NotNull Class<ExcludeUrlEntity> getEntityInterface() {
    return ExcludeUrlEntity.class;
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromAdded(@NotNull ExcludeUrlEntity entity) {
    return getRootChanges(entity);
  }

  @NotNull
  private static List<OriginChange> getRootChanges(@NotNull ExcludeUrlEntity entity) {
    ContentRootEntity root = RootsKt.getContentRoot(entity);
    if (root != null) {
      List<OriginChange> list = new ArrayList<>();
      list.add(new OriginChange(root, OriginAction.SetOrigin));
      for (SourceRootEntity sourceRoot : root.getSourceRoots()) {
        list.add(new OriginChange(sourceRoot, OriginAction.SetOrigin));
      }
      return list;
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromRemoved(@NotNull ExcludeUrlEntity entity) {
    return getRootChanges(entity);
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromReplaced(@NotNull ExcludeUrlEntity oldEntity,
                                                                  @NotNull ExcludeUrlEntity newEntity) {
    ArrayList<OriginChange> list = new ArrayList<>();
    list.addAll(getRootChanges(oldEntity));
    list.addAll(getRootChanges(newEntity));
    return list;
  }
}
