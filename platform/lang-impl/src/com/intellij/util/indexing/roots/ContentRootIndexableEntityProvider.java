// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class ContentRootIndexableEntityProvider implements IndexableEntityProvider.ModuleEntityDependent<ContentRootEntity>,
                                                    IndexableEntityProvider.Existing<ContentRootEntity> {

  @Override
  public @NotNull Class<ContentRootEntity> getEntityClass() {
    return ContentRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getIteratorBuildersForExistingModule(@NotNull ModuleEntity entity,
                                                                                                      @NotNull EntityStorage entityStorage,
                                                                                                      @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getPersistentId(), collectRootUrls(entity.getContentRoots()));
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getAddedEntityIteratorBuilders(@NotNull ContentRootEntity entity,
                                                                                                @NotNull Project project) {
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getModule().getPersistentId(), entity.getUrl());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedEntityIteratorBuilders(@NotNull ContentRootEntity oldEntity,
                                                                                                   @NotNull ContentRootEntity newEntity) {
    if (!(newEntity.getExcludedPatterns().equals(oldEntity.getExcludedPatterns()))) {
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getPersistentId(), newEntity.getUrl());
    }
    List<VirtualFileUrl> newExcludedUrls = newEntity.getExcludedUrls();
    List<VirtualFileUrl> oldExcludedUrls = oldEntity.getExcludedUrls();
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
      return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getModule().getPersistentId(), roots);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getReplacedModuleEntityIteratorBuilder(@NotNull ModuleEntity oldEntity,
                                                                                                        @NotNull ModuleEntity newEntity,
                                                                                                        @NotNull Project project) {
    List<VirtualFileUrl> newRoots = collectRootUrls(newEntity.getContentRoots());
    List<VirtualFileUrl> oldRoots = collectRootUrls(oldEntity.getContentRoots());
    return IndexableIteratorBuilders.INSTANCE.forModuleRoots(newEntity.getPersistentId(), newRoots, oldRoots);
  }

  @NotNull
  private static List<VirtualFileUrl> collectRootUrls(List<ContentRootEntity> newContentRoots) {
    return newContentRoots.stream().map(o -> o.getUrl()).filter(o -> o != null).collect(Collectors.toList());
  }

  @Override
  public @NotNull Collection<? extends IndexableIteratorBuilder> getRemovedEntityIteratorBuilders(@NotNull ContentRootEntity entity,
                                                                                                  @NotNull Project project) {
    if (!entity.getExcludedPatterns().isEmpty() || !entity.getExcludedUrls().isEmpty()) {
      VirtualFile root = ((VirtualFilePointer)entity.getUrl()).getFile();
      if (root != null && ProjectFileIndex.getInstance(project).isInContent(root)) {
        return IndexableIteratorBuilders.INSTANCE.forModuleRoots(entity.getModule().getPersistentId(), entity.getUrl());
      }
    }
    return Collections.emptyList();
  }
}
