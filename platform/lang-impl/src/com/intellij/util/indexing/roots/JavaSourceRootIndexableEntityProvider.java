// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.Function;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.JavaSourceRootEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

class JavaSourceRootIndexableEntityProvider implements IndexableEntityProvider {

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project)
    throws IndexableEntityResolvingException {
    return collectIteratorsOnAddedEntityWithDataExtractor(entity, JavaSourceRootIndexableEntityProvider::getDataToIndex, project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull WorkspaceEntity oldEntity,
                                                                                         @NotNull WorkspaceEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project)
    throws IndexableEntityResolvingException {
    return collectIteratorsOnReplacedEntityWithDataExtractor(oldEntity, newEntity, JavaSourceRootIndexableEntityProvider::getDataToIndex,
                                                             project);
  }

  @NotNull
  static Collection<IndexableFilesIterator> collectIteratorsOnAddedEntityWithDataExtractor(@NotNull WorkspaceEntity entity,
                                                                                           @NotNull Function<? super WorkspaceEntity, Pair<VirtualFile, ModuleEntity>> extractor,
                                                                                           @NotNull Project project) {
    Pair<VirtualFile, ModuleEntity> data = extractor.fun(entity);
    if (data != null) {
      return IndexableEntityProviderMethods.INSTANCE.createIterators(data.getSecond(), data.getFirst(), project);
    }
    return Collections.emptyList();
  }

  @NotNull
  static Collection<IndexableFilesIterator> collectIteratorsOnReplacedEntityWithDataExtractor(@NotNull WorkspaceEntity oldEntity,
                                                                                              @NotNull WorkspaceEntity newEntity,
                                                                                              @NotNull Function<? super WorkspaceEntity, Pair<VirtualFile, ModuleEntity>> extractor,
                                                                                              @NotNull Project project) {
    Pair<VirtualFile, ModuleEntity> newData = extractor.fun(newEntity);
    if (newData != null) {
      Pair<VirtualFile, ModuleEntity> oldData = extractor.fun(oldEntity);
      if (oldData == null || !newData.getFirst().equals(oldData.getFirst())) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(newData.getSecond(), newData.getFirst(), project);
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static Pair<VirtualFile, ModuleEntity> getDataToIndex(WorkspaceEntity entity) {
    if (entity instanceof JavaSourceRootEntity) {
      SourceRootEntity sourceRootEntity = ((JavaSourceRootEntity)entity).getSourceRoot();
      VirtualFilePointer url = (VirtualFilePointer)sourceRootEntity.getUrl();
      if (url.isValid()) {
        return new Pair<>(url.getFile(), sourceRootEntity.getContentRoot().getModule());
      }
    }
    return null;
  }
}
