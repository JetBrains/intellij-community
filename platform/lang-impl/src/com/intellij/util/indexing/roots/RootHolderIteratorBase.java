// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.platform.workspace.storage.EntityReference;
import com.intellij.util.indexing.roots.origin.IndexingRootHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public abstract class RootHolderIteratorBase implements IndexableFilesIterator {
  private final boolean iterateNonProjectFiles;
  protected final @NotNull EntityReference<?> entityReference;
  private final @NotNull IndexableIteratorPresentation presentation;
  protected final @NotNull IndexingRootHolder roots;

  protected RootHolderIteratorBase(@NotNull EntityReference<?> entityReference,
                                   @NotNull IndexingRootHolder roots,
                                   @NotNull IndexableIteratorPresentation presentation) {
    this(entityReference, roots, presentation, false);
  }

  protected RootHolderIteratorBase(@NotNull EntityReference<?> entityReference,
                                   @NotNull IndexingRootHolder roots,
                                   @NotNull IndexableIteratorPresentation presentation,
                                   boolean iterateNonProjectFiles) {
    this.entityReference = entityReference;
    this.roots = roots.immutableCopyOf();
    this.presentation = presentation;
    this.iterateNonProjectFiles = iterateNonProjectFiles;
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
  public final boolean iterateFiles(@NotNull Project project, @NotNull ContentIterator fileIterator, @NotNull VirtualFileFilter fileFilter) {
    return IndexableFilesIterationMethods.INSTANCE.iterateRoots(project, roots, fileIterator, fileFilter, !iterateNonProjectFiles);
  }

  @Override
  public final @NotNull Set<String> getRootUrls(@NotNull Project project) {
    return roots.getRootUrls();
  }
}
