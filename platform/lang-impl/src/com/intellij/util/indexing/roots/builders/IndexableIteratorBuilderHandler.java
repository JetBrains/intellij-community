// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots.builders;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.indexing.roots.IndexableEntityProvider;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface IndexableIteratorBuilderHandler {
  ExtensionPointName<IndexableIteratorBuilderHandler> EP_NAME =
    new ExtensionPointName<>("com.intellij.indexableIteratorBuilderHandler");

  boolean accepts(@NotNull IndexableEntityProvider.IndexableIteratorBuilder builder);

  @NotNull
  List<? extends IndexableFilesIterator> instantiate(@NotNull Collection<IndexableEntityProvider.IndexableIteratorBuilder> builders,
                                                     @NotNull Project project,
                                                     @NotNull WorkspaceEntityStorage entityStorage);
}
