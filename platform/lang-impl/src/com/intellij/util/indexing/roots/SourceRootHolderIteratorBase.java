// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.platform.workspace.storage.EntityPointer;
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class SourceRootHolderIteratorBase implements IndexableFilesIterator {
  protected final @NotNull EntityPointer<?> myEntityPointer;
  private final @NotNull IndexableIteratorPresentation presentation;
  protected final @NotNull IndexingSourceRootHolder roots;

  protected SourceRootHolderIteratorBase(@NotNull EntityPointer<?> entityPointer,
                                         @NotNull IndexingSourceRootHolder roots,
                                         @NotNull IndexableIteratorPresentation presentation) {
    this.myEntityPointer = entityPointer;
    this.roots = roots.immutableCopyOf();
    this.presentation = presentation;
  }

  @Override
  public String getDebugName() {
    return presentation.getDebugName();
  }

  @Override
  public String getIndexingProgressText() {
    return presentation.getIndexingProgressText();
  }

  @Override
  public String getRootsScanningProgressText() {
    return presentation.getRootsScanningProgressText();
  }

  @Override
  public final boolean iterateFiles(@NotNull Project project,
                                    @NotNull ContentIterator fileIterator,
                                    @NotNull VirtualFileFilter fileFilter) {
    return IndexableFilesIterationMethods.INSTANCE.iterateRoots(project, roots, fileIterator, fileFilter, true);
  }

  @Override
  public final @NotNull Set<String> getRootUrls(@NotNull Project project) {
    return roots.getRootUrls();
  }
}
