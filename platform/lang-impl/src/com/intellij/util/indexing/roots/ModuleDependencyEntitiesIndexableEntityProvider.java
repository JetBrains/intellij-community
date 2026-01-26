// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.*;
import com.intellij.util.SmartList;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class ModuleDependencyEntitiesIndexableEntityProvider implements IndexableEntityProvider.Enforced<ModuleEntity> {

  @Override
  public @NotNull Class<ModuleEntity> getEntityClass() {
    return ModuleEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleEntity entity,
                                                                                                @NotNull Project project) {
    List<IndexableIteratorBuilder> iterators = new SmartList<>();
    iterators.addAll(IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getSymbolicId()));
    return iterators;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ModuleEntity oldEntity,
                                                                                                   @NotNull ModuleEntity newEntity,
                                                                                                   @NotNull Project project) {
    return Collections.emptyList();
  }
}
