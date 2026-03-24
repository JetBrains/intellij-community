// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
public final class ModuleDependencyEntitiesIndexableEntityProvider implements IndexableEntityProvider.Enforced<ModuleEntity> {

  @Override
  public @NotNull Class<ModuleEntity> getEntityClass() {
    return ModuleEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ModuleEntity entity,
                                                                                                @NotNull Project project) {
    if (Registry.is("use.workspace.file.index.for.partial.scanning")) {
      return Collections.emptyList();
    } else {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getSymbolicId());
    }
  }
}
