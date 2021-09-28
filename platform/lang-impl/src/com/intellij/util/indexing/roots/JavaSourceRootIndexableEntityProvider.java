// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

class JavaSourceRootIndexableEntityProvider implements IndexableEntityProvider<JavaSourceRootEntity> {

  @Override
  public @NotNull Class<JavaSourceRootEntity> getEntityClass() {
    return JavaSourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaSourceRootEntity entity,
                                                                                                @NotNull Project project) {
    return collectBuildersOnAddedEntityWithDataExtractor(entity, JavaSourceRootIndexableEntityProvider::getDataForBuilders);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaSourceRootEntity oldEntity,
                                                                                                   @NotNull JavaSourceRootEntity newEntity) {
    return collectBuildersOnReplacedEntityWithDataExtractor(oldEntity, newEntity,
                                                            JavaSourceRootIndexableEntityProvider::getDataForBuilders);
  }

  static <E extends WorkspaceEntity> Collection<IndexableIteratorBuilder> collectBuildersOnAddedEntityWithDataExtractor(@NotNull E entity,
                                                                                                                        @NotNull Function<? super E, @NotNull Pair<VirtualFileUrl, ModuleEntity>> extractor) {
    Pair<VirtualFileUrl, ModuleEntity> data = extractor.fun(entity);
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(data.getSecond().persistentId(), data.getFirst());
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
        return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newData.getSecond().persistentId(), newData.getFirst());
      }
    }
    return Collections.emptyList();
  }

  private static @NotNull Pair<VirtualFileUrl, ModuleEntity> getDataForBuilders(@NotNull JavaSourceRootEntity entity) {
    SourceRootEntity sourceRootEntity = entity.getSourceRoot();
    return new Pair<>(sourceRootEntity.getUrl(), sourceRootEntity.getContentRoot().getModule());
  }
}
