// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.workspaceModel.storage.EntityStorage;
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.List;

public interface IndexableFilesIndex {
  @VisibleForTesting
  Key<Boolean> ENABLE_IN_TESTS = new Key<>("enable.IndexableFilesIndex");

  /**
   * See {@link com.intellij.util.indexing.roots.StandardContributorsKt#shouldIndexProjectBasedOnIndexableEntityProviders()}
   */
  static boolean isEnabled() {
   return (Registry.is("indexing.use.indexable.files.index") ||
           (ApplicationManager.getApplication().isUnitTestMode() && TestModeFlags.is(ENABLE_IN_TESTS))) &&
          Registry.is("indexing.enable.entity.provider.based.indexing");
  }

  @NotNull
  static IndexableFilesIndex getInstance(@NotNull Project project) {
    assert isEnabled();
    return project.getService(IndexableFilesIndex.class);
  }

  @RequiresBackgroundThread
  boolean shouldBeIndexed(@NotNull VirtualFile file);

  @RequiresBackgroundThread
  @NotNull
  List<IndexableFilesIterator> getIndexingIterators();

  @RequiresBackgroundThread
  @NotNull
  Collection<IndexableFilesIterator> getModuleIndexingIterators(@NotNull ModuleEntity entity, @NotNull EntityStorage entityStorage);
}