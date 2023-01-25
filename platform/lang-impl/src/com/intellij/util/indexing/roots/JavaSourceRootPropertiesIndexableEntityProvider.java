// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootPropertiesEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

class JavaSourceRootPropertiesIndexableEntityProvider implements IndexableEntityProvider.Enforced<JavaSourceRootPropertiesEntity> {

  @Override
  public @NotNull Class<JavaSourceRootPropertiesEntity> getEntityClass() {
    return JavaSourceRootPropertiesEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(SourceRootEntity.class, JavaSourceRootPropertiesIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaSourceRootPropertiesEntity entity,
                                                                                                @NotNull Project project) {
    return collectBuildersOnAddedEntityWithDataExtractor(entity, JavaSourceRootPropertiesIndexableEntityProvider::getDataForBuilders);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaSourceRootPropertiesEntity oldEntity,
                                                                                                   @NotNull JavaSourceRootPropertiesEntity newEntity,
                                                                                                   @NotNull Project project) {
    return collectBuildersOnReplacedEntityWithDataExtractor(oldEntity, newEntity,
                                                            JavaSourceRootPropertiesIndexableEntityProvider::getDataForBuilders);
  }

  static <E extends WorkspaceEntity> Collection<IndexableIteratorBuilder> collectBuildersOnAddedEntityWithDataExtractor(@NotNull E entity,
                                                                                                                        @NotNull Function<? super E, @NotNull Pair<VirtualFileUrl, ModuleEntity>> extractor) {
    Pair<VirtualFileUrl, ModuleEntity> data = extractor.fun(entity);
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(data.getSecond().getSymbolicId(), data.getFirst());
  }

  @NotNull
  static <E extends WorkspaceEntity> Collection<? extends IndexableIteratorBuilder> collectBuildersOnReplacedEntityWithDataExtractor(
    @NotNull E oldEntity,
    @NotNull E newEntity,
    @NotNull Function<? super E, Pair<VirtualFileUrl, ModuleEntity>> extractor) {
    Pair<VirtualFileUrl, ModuleEntity> newData = extractor.fun(newEntity);
    if (newData != null) {
      Pair<VirtualFileUrl, ModuleEntity> oldData = extractor.fun(oldEntity);
      if (oldData == null || !newData.getFirst().equals(oldData.getFirst())) {
        return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newData.getSecond().getSymbolicId(), newData.getFirst());
      }
    }
    return Collections.emptyList();
  }

  private static @NotNull Pair<VirtualFileUrl, ModuleEntity> getDataForBuilders(@NotNull JavaSourceRootPropertiesEntity entity) {
    SourceRootEntity sourceRootEntity = entity.getSourceRoot();
    return new Pair<>(sourceRootEntity.getUrl(), sourceRootEntity.getContentRoot().getModule());
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull SourceRootEntity oldEntity,
                                                                                                                @NotNull SourceRootEntity newEntity) {
    if (oldEntity.getJavaSourceRoots().equals(newEntity.getJavaSourceRoots())) return Collections.emptyList();
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getContentRoot().getModule().getSymbolicId(), newEntity.getUrl());
  }
}
