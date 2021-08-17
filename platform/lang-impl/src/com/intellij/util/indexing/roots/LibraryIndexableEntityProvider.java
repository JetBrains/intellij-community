// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.workspaceModel.ide.WorkspaceModel;
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootsChangeListener;
import com.intellij.workspaceModel.storage.WorkspaceEntity;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

@ApiStatus.Internal
@ApiStatus.Experimental
class LibraryIndexableEntityProvider implements IndexableEntityProvider {
  private static final Logger LOG = Logger.getInstance(LibraryIndexableEntityProvider.class);

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getAddedEntityIterator(@NotNull WorkspaceEntity entity,
                                                                                      @NotNull WorkspaceEntityStorage storage,
                                                                                      @NotNull Project project)
    throws IndexableEntityResolvingException {
    return createIterators(entity, project);
  }

  @Override
  public @NotNull Collection<? extends IndexableFilesIterator> getReplacedEntityIterator(@NotNull WorkspaceEntity oldEntity,
                                                                                         @NotNull WorkspaceEntity newEntity,
                                                                                         @NotNull WorkspaceEntityStorage storage,
                                                                                         @NotNull Project project)
    throws IndexableEntityResolvingException {
    return createIterators(newEntity, project);
  }

  @NotNull
  static Collection<? extends IndexableFilesIterator> createIterators(@Nullable WorkspaceEntity entity,
                                                                      @NotNull Project project) {
    if (entity instanceof LibraryEntity &&
        ProjectRootsChangeListener.Companion.shouldFireRootsChanged$intellij_platform_lang_impl(entity, project)) {
      WorkspaceEntityStorage entityStorage = WorkspaceModel.getInstance(project).getEntityStorage().getCurrent();
      Library library = IndexableEntityProviderMethods.INSTANCE.findLibraryForEntity((LibraryEntity)entity, entityStorage);
      LOG.assertTrue(library != null, "Failed to find library " + ((LibraryEntity)entity).getName());
      return IndexableEntityProviderMethods.INSTANCE.createIterators(library);
    }
    return Collections.emptyList();
  }
}
