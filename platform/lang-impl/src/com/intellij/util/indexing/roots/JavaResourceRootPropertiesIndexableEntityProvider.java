// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity;
import com.intellij.java.workspace.entities.JavaRootsKt;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.jps.entities.SourceRootEntity;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static com.intellij.util.indexing.roots.JavaSourceRootPropertiesIndexableEntityProvider.collectBuildersOnAddedEntityWithDataExtractor;
import static com.intellij.util.indexing.roots.JavaSourceRootPropertiesIndexableEntityProvider.collectBuildersOnReplacedEntityWithDataExtractor;

final class JavaResourceRootPropertiesIndexableEntityProvider implements IndexableEntityProvider.Enforced<JavaResourceRootPropertiesEntity> {
  @Override
  public @NotNull Class<JavaResourceRootPropertiesEntity> getEntityClass() {
    return JavaResourceRootPropertiesEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(SourceRootEntity.class, JavaResourceRootPropertiesIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaResourceRootPropertiesEntity entity,
                                                                                                @NotNull Project project) {
    return collectBuildersOnAddedEntityWithDataExtractor(entity, JavaResourceRootPropertiesIndexableEntityProvider::getDataForBuilders);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaResourceRootPropertiesEntity oldEntity,
                                                                                                   @NotNull JavaResourceRootPropertiesEntity newEntity,
                                                                                                   @NotNull Project project) {
    return collectBuildersOnReplacedEntityWithDataExtractor(oldEntity, newEntity,
                                                            JavaResourceRootPropertiesIndexableEntityProvider::getDataForBuilders);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull JavaResourceRootPropertiesEntity entity,
                                                                                                  @NotNull Project project) {
    return getAddedEntityIteratorBuilders(entity, project);
  }

  private static @NotNull Pair<VirtualFileUrl, ModuleEntity> getDataForBuilders(@NotNull JavaResourceRootPropertiesEntity entity) {
    SourceRootEntity sourceRootEntity = entity.getSourceRoot();
    return new Pair<>(sourceRootEntity.getUrl(), sourceRootEntity.getContentRoot().getModule());
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull SourceRootEntity oldEntity,
                                                                                                                @NotNull SourceRootEntity newEntity) {
    if (JavaRootsKt.getJavaSourceRoots(oldEntity).equals(JavaRootsKt.getJavaSourceRoots(newEntity))) return Collections.emptyList();
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getContentRoot().getModule().getSymbolicId(), newEntity.getUrl());
  }
}
