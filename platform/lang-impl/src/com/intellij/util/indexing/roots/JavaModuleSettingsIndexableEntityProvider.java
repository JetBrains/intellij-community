// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity;
import com.intellij.openapi.project.Project;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class JavaModuleSettingsIndexableEntityProvider implements IndexableEntityProvider.Enforced<JavaModuleSettingsEntity> {

  @Override
  public @NotNull Class<JavaModuleSettingsEntity> getEntityClass() {
    return JavaModuleSettingsEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(
      DependencyOnParent.create(ModuleEntity.class, JavaModuleSettingsIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity entity,
                                                                                                @NotNull Project project) {
    if (entity.getLanguageLevelId() != null) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(entity.getModule().getSymbolicId());
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity entity,
                                                                                                  @NotNull Project project) {
    return getAddedEntityIteratorBuilders(entity, project);
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull JavaModuleSettingsEntity oldEntity,
                                                                                                   @NotNull JavaModuleSettingsEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (!Objects.equals(newEntity.getLanguageLevelId(), oldEntity.getLanguageLevelId())) {
      return IndexableIteratorBuilders.INSTANCE.forModuleContent(newEntity.getModule().getSymbolicId());
    }
    return Collections.emptyList();
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                                @NotNull ModuleEntity newEntity) {
    List<VirtualFileUrl> newRootUrls = collectRootUrls(newEntity.getContentRoots());
    List<VirtualFileUrl> oldRootUrls = collectRootUrls(oldEntity.getContentRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getSymbolicId(), newRootUrls, oldRootUrls);
  }

  private static @NotNull List<VirtualFileUrl> collectRootUrls(List<? extends ContentRootEntity> newContentRoots) {
    return ContainerUtil.map(newContentRoots, o -> o.getUrl());
  }
}
