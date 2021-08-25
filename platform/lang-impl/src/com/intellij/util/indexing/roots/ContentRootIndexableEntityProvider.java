// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.url.VirtualFileUrl;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class ContentRootIndexableEntityProvider implements IndexableEntityProvider {

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (entity instanceof ContentRootEntity) {
      ContentRootEntity contentRootEntity = (ContentRootEntity)entity;
      return IndexableEntityProviderMethods.INSTANCE.createIterators(contentRootEntity.getModule(), getVirtualFile(contentRootEntity),
                                                                     project);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull WorkspaceEntity oldEntity,
                                                                                         @NotNull WorkspaceEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (newEntity instanceof ModuleEntity) {
      List<VirtualFile> newRoots = collectRoots(((ModuleEntity)newEntity).getContentRoots());
      List<VirtualFile> oldRoots = collectRoots(((ModuleEntity)oldEntity).getContentRoots());
      return IndexableEntityProviderMethods.INSTANCE.createIterators((ModuleEntity)newEntity, newRoots, oldRoots, project);
    }
    else if (newEntity instanceof ContentRootEntity) {
      ContentRootEntity newContentRoot = (ContentRootEntity)newEntity;
      ContentRootEntity oldContentRoot = (ContentRootEntity)oldEntity;

      if (!(newContentRoot.getExcludedPatterns().equals(oldContentRoot.getExcludedPatterns()))) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(newContentRoot.getModule(), getVirtualFile(newContentRoot),
                                                                       project);
      }
      List<VirtualFileUrl> newExcludedUrls = newContentRoot.getExcludedUrls();
      List<VirtualFileUrl> oldExcludedUrls = oldContentRoot.getExcludedUrls();
      if (!oldExcludedUrls.equals(newExcludedUrls)) {
        VirtualFileManager fileManager = VirtualFileManager.getInstance();
        List<VirtualFile> roots = new ArrayList<>();
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
            VirtualFile file = fileManager.findFileByUrl(oldPath);
            if (file != null) {
              roots.add(file);
            }
          }
        }
        return IndexableEntityProviderMethods.INSTANCE.createIterators(newContentRoot.getModule(), roots, project);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<VirtualFile> collectRoots(Sequence<ContentRootEntity> newContentRoots) {
    return SequencesKt.toList(SequencesKt.mapNotNull(newContentRoots, root -> getVirtualFile(root)));
  }

  @Nullable
  private static VirtualFile getVirtualFile(@NotNull ContentRootEntity contentRoot) {
    VirtualFilePointer url = (VirtualFilePointer)contentRoot.getUrl();
    return url.getFile();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getRemovedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                        @NotNull WorkspaceEntityStorage storage,
                                                                                        @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (entity instanceof ContentRootEntity) {
      ContentRootEntity contentRootEntity = (ContentRootEntity)entity;
      if (!contentRootEntity.getExcludedPatterns().isEmpty() || !contentRootEntity.getExcludedUrls().isEmpty()) {
        VirtualFile root = getVirtualFile(contentRootEntity);
        if (root != null && ProjectFileIndex.getInstance(project).isInContent(root)) {
          return IndexableEntityProviderMethods.INSTANCE.createIterators(((ContentRootEntity)entity).getModule(),
                                                                         Collections.singletonList(root), project);
        }
      }
    }
    return Collections.emptyList();
  }
}
