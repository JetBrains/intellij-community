// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.workspaceModel.storage.bridgeEntities.api.JavaResourceRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.SourceRootEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.util.indexing.roots.JavaSourceRootIndexableEntityProvider.collectBuildersOnAddedEntityWithDataExtractor;
import static com.intellij.util.indexing.roots.JavaSourceRootIndexableEntityProvider.collectBuildersOnReplacedEntityWithDataExtractor;

class JavaResourceRootIndexableEntityProvider implements IndexableEntityProvider<JavaResourceRootEntity> {
  @Override
  public @NotNull Class<JavaResourceRootEntity> getEntityClass() {
    return JavaResourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaResourceRootEntity entity,
                                                                                                @NotNull Project project) {
    return collectBuildersOnAddedEntityWithDataExtractor(entity, JavaResourceRootIndexableEntityProvider::getDataForBuilders);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaResourceRootEntity oldEntity,
                                                                                                   @NotNull JavaResourceRootEntity newEntity) {
    return collectBuildersOnReplacedEntityWithDataExtractor(oldEntity, newEntity,
                                                            JavaResourceRootIndexableEntityProvider::getDataForBuilders);
  }

  @NotNull
  private static Pair<VirtualFileUrl, ModuleEntity> getDataForBuilders(@NotNull JavaResourceRootEntity entity) {
    SourceRootEntity sourceRootEntity = entity.getSourceRoot();
    return new Pair<>(sourceRootEntity.getUrl(), sourceRootEntity.getContentRoot().getModule());
  }
}
