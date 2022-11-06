// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


public class ContentRootEntityInducedChangesProvider implements IndexableEntityInducedChangesProvider<ContentRootEntity> {

  @Override
  public @NotNull Class<ContentRootEntity> getEntityInterface() {
    return ContentRootEntity.class;
  }

  @Override
  public @NotNull Collection<OriginChange> getChangesFromReplaced(@NotNull ContentRootEntity oldEntity,
                                                                  @NotNull ContentRootEntity newEntity) {
    List<OriginChange> old = createOriginChanges(oldEntity, OriginAction.RemoveOrigin);
    List<OriginChange> newer = createOriginChanges(newEntity, OriginAction.SetOrigin);
    ArrayList<OriginChange> list = new ArrayList<>(old.size() + newer.size());
    list.addAll(old);
    list.addAll(newer);
    return list;
  }

  @NotNull
  private static List<OriginChange> createOriginChanges(ContentRootEntity change, OriginAction setOrigin) {
    return ContainerUtil.map(change.getSourceRoots(), root -> new OriginChange(root, setOrigin));
  }

  @Override
  public @NotNull Collection<OriginChange> getInducedChangesFromRefresh(@NotNull ContentRootEntity entity) {
    return createOriginChanges(entity, OriginAction.SetOrigin);
  }
}
