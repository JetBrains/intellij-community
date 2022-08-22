// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
@ApiStatus.Internal
public class IndexableFilesIndex {
  private static final Logger LOG = Logger.getInstance(IndexableFilesIndex.class);

  public static boolean shouldBeUsed() {
    return Registry.is("indexing.use.indexable.files.index") &&
           DefaultProjectIndexableFilesContributor.Companion.indexProjectBasedOnIndexableEntityProviders();
  }

  @NotNull
  public static IndexableFilesIndex getInstance(@NotNull Project project) {
    LOG.assertTrue(shouldBeUsed());
    return project.getService(IndexableFilesIndex.class);
  }

  public IndexableFilesIndex(@NotNull Project project) {
  }
}