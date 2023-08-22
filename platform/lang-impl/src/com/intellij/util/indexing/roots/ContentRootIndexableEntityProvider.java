// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.platform.workspace.jps.entities.ContentRootEntity;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityStorage;
import com.intellij.platform.workspace.storage.WorkspaceEntity;
import com.intellij.platform.workspace.storage.url.VirtualFileUrl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.util.indexing.roots.IndexableEntityProvider.DependencyOnParent.create;

final class ContentRootIndexableEntityProvider implements IndexableEntityProvider.Existing<ContentRootEntity> {

  @Override
  public @NotNull Class<ContentRootEntity> getEntityClass() {
    return ContentRootEntity.class;
  }

  @Override
  public @NotNull Collection<DependencyOnParent<? extends WorkspaceEntity>> getDependencies() {
    return Collections.singletonList(create(ModuleEntity.class, ContentRootIndexableEntityProvider::getReplacedParentEntityIteratorBuilder));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getSymbolicId(), collectRootUrls(entity.getContentRoots()));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ContentRootEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getModule().getSymbolicId(), entity.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ContentRootEntity oldEntity,
                                                                                                   @NotNull ContentRootEntity newEntity,
                                                                                                   @NotNull Project project) {
    if (!(newEntity.getExcludedPatterns().equals(oldEntity.getExcludedPatterns()))) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getSymbolicId(), newEntity.getUrl());
    }
    List<VirtualFileUrl> newExcludedUrls = ContainerUtil.map(newEntity.getExcludedUrls(), o -> o.getUrl());
    List<VirtualFileUrl> oldExcludedUrls = ContainerUtil.map(oldEntity.getExcludedUrls(), o -> o.getUrl());
    if (!oldExcludedUrls.equals(newExcludedUrls)) {
      List<VirtualFileUrl> roots = new ArrayList<>();
      for (VirtualFileUrl oldUrl : oldExcludedUrls) {
        boolean found = false;
        String oldPath = oldUrl.getUrl();
        for (VirtualFileUrl newUrl : newExcludedUrls) {
          if (VfsUtilCore.isEqualOrAncestor(newUrl.getUrl(), oldPath)) {
            found = true;
            break;
          }
        }
        if (!found) {
          roots.add(oldUrl);
        }
      }
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getSymbolicId(), roots);
    }
    return Collections.emptyList();
  }

  @NotNull
  static List<VirtualFileUrl> collectRootUrls(List<? extends ContentRootEntity> newContentRoots) {
    return ContainerUtil.map(newContentRoots, o -> o.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull ContentRootEntity entity,
                                                                                                  @NotNull Project project) {
    if (!entity.getExcludedPatterns().isEmpty() || !entity.getExcludedUrls().isEmpty()) {
      VirtualFile root = ((VirtualFilePointer)entity.getUrl()).getFile();
      if (root != null && ProjectFileIndex.getInstance(project).isInContent(root)) {
        return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getModule().getSymbolicId(), entity.getUrl());
      }
    }
    return Collections.emptyList();
  }

  private static @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedParentEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                                @NotNull ModuleEntity newEntity) {
    List<VirtualFileUrl> newRoots = collectRootUrls(newEntity.getContentRoots());
    List<VirtualFileUrl> oldRoots = collectRootUrls(oldEntity.getContentRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getSymbolicId(), newRoots, oldRoots);
  }
}
