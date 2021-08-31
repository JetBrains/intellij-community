// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
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

class SourceRootIndexableEntityProvider implements IndexableEntityProvider.ModuleEntityDependent<SourceRootEntity>,
                                                   IndexableEntityProvider.Existing<SourceRootEntity> {

  @Override
  public @NotNull Class<SourceRootEntity> getEntityClass() {
    return SourceRootEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getExistingEntityForModuleIterator(@NotNull SourceRootEntity entity,
                                                                                                  @NotNull ModuleEntity moduleEntity,
                                                                                                  @NotNull WorkspaceEntityStorage entityStorage,
                                                                                                  @NotNull Project project) {
    if (moduleEntity.equals(entity.getContentRoot().getModule())) {
      return getExistingEntityIterator(entity, entityStorage, project);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull SourceRootEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project) {
    return IndexableEntityProviderMethods.INSTANCE.createIterators(entity.getContentRoot().getModule(),
                                                                   getVirtualFile(entity),
                                                                   project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull SourceRootEntity oldEntity,
                                                                                         @NotNull SourceRootEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    if (!(newEntity.getUrl().equals(oldEntity.getUrl())) || !newEntity.getRootType().equals(oldEntity.getRootType())) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity.getContentRoot().getModule(),
                                                                     getVirtualFile(newEntity), project);
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedModuleEntityIterator(@NotNull ModuleEntity oldEntity,
                                                                                               @NotNull ModuleEntity newEntity,
                                                                                               @NotNull WorkspaceEntityStorage storage,
                                                                                               @NotNull Project project) {
    List<VirtualFile> newRoots = collectRoots(newEntity.getSourceRoots());
    List<VirtualFile> oldRoots = collectRoots(oldEntity.getSourceRoots());
    return IndexableEntityProviderMethods.INSTANCE.createIterators(newEntity, newRoots, oldRoots, project);
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
