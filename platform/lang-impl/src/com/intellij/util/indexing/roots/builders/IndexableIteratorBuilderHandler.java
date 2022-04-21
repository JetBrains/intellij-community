// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.builders;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.workspaceModel.storage.EntityStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Should be used together with {@link IndexableEntityProvider} and {@link IndexableEntityProvider.IndexableIteratorBuilder}
 * to provide indexing policy for a custom workspace model entity.
 * <p>
 * It's possible that such code would also need a custom {@link IndexableFilesIterator} to implement.
 */
@ApiStatus.OverrideOnly
public interface IndexableIteratorBuilderHandler {
  ExtensionPointName<IndexableIteratorBuilderHandler> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableIteratorBuilderHandler");

  boolean accepts(@NotNull IndexableEntityProvider.IndexableIteratorBuilder builder);

  @NotNull
  List<? extends IndexableFilesIterator> instantiate(@NotNull Collection<IndexableEntityProvider.IndexableIteratorBuilder> builders,
                                                     @NotNull Project project,
                                                     @NotNull EntityStorage entityStorage);
}
