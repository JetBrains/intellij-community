// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import kotlin.sequences.Sequence;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class SourceRootIndexableEntityProvider implements IndexableEntityProvider {

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project)
    throws IndexableEntityResolvingException {
    if (entity instanceof SourceRootEntity) {
      SourceRootEntity sourceRootEntity = (SourceRootEntity)entity;
      return IndexableEntityProviderMethods.INSTANCE.createIterators(sourceRootEntity.getContentRoot().getModule(),
                                                                     getVirtualFile(sourceRootEntity),
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
      List<VirtualFile> newRoots = collectRoots(((ModuleEntity)newEntity).getSourceRoots());
      List<VirtualFile> oldRoots = collectRoots(((ModuleEntity)oldEntity).getSourceRoots());
      return IndexableEntityProviderMethods.INSTANCE.createIterators((ModuleEntity)newEntity, newRoots, oldRoots, project);
    }
    else if (newEntity instanceof SourceRootEntity) {
      SourceRootEntity newSourceRoot = (SourceRootEntity)newEntity;
      SourceRootEntity oldSourceRoot = (SourceRootEntity)oldEntity;

      if (!(newSourceRoot.getUrl().equals(oldSourceRoot.getUrl())) || !newSourceRoot.getRootType().equals(oldSourceRoot.getRootType())) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(newSourceRoot.getContentRoot().getModule(),
                                                                       getVirtualFile(newSourceRoot), project);
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<VirtualFile> collectRoots(Sequence<SourceRootEntity> newContentRoots) {
    return SequencesKt.toList(SequencesKt.mapNotNull(newContentRoots, root -> getVirtualFile(root)));
  }

  @Nullable
  private static VirtualFile getVirtualFile(@NotNull SourceRootEntity sourceRoot) {
    VirtualFilePointer url = (VirtualFilePointer)sourceRoot.getUrl();
    return url.getFile();
  }
}
