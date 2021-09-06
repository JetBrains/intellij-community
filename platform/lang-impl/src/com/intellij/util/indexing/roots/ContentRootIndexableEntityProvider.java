// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
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

class ContentRootIndexableEntityProvider implements IndexableEntityProvider.ModuleEntityDependent<ContentRootEntity>,
                                                    IndexableEntityProvider.Existing<ContentRootEntity> {

  @Override
  public @NotNull Class<ContentRootEntity> getEntityClass() {
    return ContentRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getIteratorsForExistingModule(@NotNull ModuleEntity entity,
                                                                                   @NotNull WorkspaceEntityStorage entityStorage,
                                                                                   @NotNull Project project) {

    List<VirtualFile> roots = collectRoots(entity.getContentRoots());
    return IndexableEntityProviderMethods.INSTANCE.createIterators(entity, roots, project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull ContentRootEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project) {
    return IndexableEntityProviderMethods.INSTANCE.createIterators(entity.getModule(), getVirtualFile(entity), project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull ContentRootEntity oldEntity,
                                                                                         @NotNull ContentRootEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    if (!(newEntity.getExcludedPatterns().equals(oldEntity.getExcludedPatterns()))) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity.getModule(), getVirtualFile(newEntity),
                                                                     project);
    }
    List<VirtualFileUrl> newExcludedUrls = newEntity.getExcludedUrls();
    List<VirtualFileUrl> oldExcludedUrls = oldEntity.getExcludedUrls();
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
      return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity.getModule(), roots, project);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedModuleEntityIterator(@NotNull ModuleEntity oldEntity,
                                                                                               @NotNull ModuleEntity newEntity,
                                                                                               @NotNull WorkspaceEntityStorage storage,
                                                                                               @NotNull Project project) {
    List<VirtualFile> newRoots = collectRoots(newEntity.getContentRoots());
    List<VirtualFile> oldRoots = collectRoots(oldEntity.getContentRoots());
    return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity, newRoots, oldRoots, project);
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
  public @NotNull Collection<? extends IndexableFilesIterator> getRemovedEntityIterator(@NotNull ContentRootEntity entity,
                                                                                        @NotNull WorkspaceEntityStorage storage,
                                                                                        @NotNull Project project) {
    if (!entity.getExcludedPatterns().isEmpty() || !entity.getExcludedUrls().isEmpty()) {
      VirtualFile root = getVirtualFile(entity);
      if (root != null && ProjectFileIndex.getInstance(project).isInContent(root)) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(entity.getModule(),
                                                                       Collections.singletonList(root), project);
      }
    }
    return Collections.emptyList();
  }
}
