// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.CustomSourceRootPropertiesEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

class CustomSourceRootPropertyIndexableEntityProvider implements IndexableEntityProvider.Enforced<CustomSourceRootPropertiesEntity> {

  @Override
  public @NotNull Class<CustomSourceRootPropertiesEntity> getEntityClass() {
    return CustomSourceRootPropertiesEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(SourceRootEntity.class,
                                CustomSourceRootPropertyIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull CustomSourceRootPropertiesEntity entity,
                                                                                                @NotNull Project project) {
    return createIterators(entity.getSourceRoot());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull CustomSourceRootPropertiesEntity oldEntity,
                                                                                                   @NotNull CustomSourceRootPropertiesEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (!Objects.equals(oldEntity.getPropertiesXmlTag(), newEntity.getPropertiesXmlTag())) {
      return createIterators(newEntity.getSourceRoot());
    }
    return Collections.emptyList();
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull SourceRootEntity oldEntity,
                                                                                                                @NotNull SourceRootEntity newEntity) {
    if ((oldEntity.getCustomSourceRootProperties() == null) != (newEntity.getCustomSourceRootProperties() == null)) {
      return createIterators(newEntity);
    }
    if (newEntity.getCustomSourceRootProperties() != null &&
        !Objects.equals(oldEntity.getCustomSourceRootProperties().getPropertiesXmlTag(),
                        newEntity.getCustomSourceRootProperties().getPropertiesXmlTag())) {
      return createIterators(newEntity);
    }
    return Collections.emptyList();
  }

  private static Collection<? extends IndexableIteratorBuilder> createIterators(@NotNull SourceRootEntity entity) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getContentRoot().getModule().getSymbolicId(), entity.getUrl());
  }
}
