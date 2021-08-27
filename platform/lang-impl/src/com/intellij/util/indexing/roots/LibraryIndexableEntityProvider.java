// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
@ApiStatus.Experimental
class LibraryIndexableEntityProvider implements IndexableEntityProvider<LibraryEntity> {

  @Override
  public @NotNull Class<LibraryEntity> getEntityClass() {
    return LibraryEntity.class;
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getExistingEntityForModuleIterator(@NotNull LibraryEntity entity,
                                                                                                  @NotNull ModuleEntity moduleEntity,
                                                                                                  @NotNull WorkspaceEntityStorage entityStorage,
                                                                                                  @NotNull Project project) {
    return Collections.emptyList();
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull LibraryEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project) {
    return createIterators(entity, project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull LibraryEntity oldEntity,
                                                                                         @NotNull LibraryEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project) {
    return createIterators(newEntity, project);
  }

  @NotNull
  static Collection<? extends IndexableFilesIterator> createIterators(@Nullable LibraryEntity entity,
                                                                      @NotNull Project project) {
    if (entity != null &&
        ProjectRootsChangeListener.Companion.shouldFireRootsChanged$intellij_platform_lang_impl(entity, project)) {
      WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
      Library library = IndexableEntityProviderMethods.INSTANCE.findLibraryForEntity(entity, entityStorage);
      if (library != null) {
        return IndexableEntityProviderMethods.INSTANCE.createIterators(library);
      }
    }
    return Collections.emptyList();
  }
}
