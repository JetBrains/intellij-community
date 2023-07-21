// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.indexing.customizingIteration.ExternalEntityIndexableIterator;
import com.intellij.util.indexing.roots.origin.ExternalEntityOrigin;
import com.intellij.util.indexing.roots.origin.ExternalEntityOriginImpl;
import com.intellij.util.indexing.roots.origin.IndexingSourceRootHolder;
import com.intellij.platform.workspace.storage.EntityReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class ExternalEntityIndexableIteratorImpl implements ExternalEntityIndexableIterator {
  private final EntityReference<?> entityReference;
  private final IndexingSourceRootHolder roots;
  @Nullable @NlsContexts.ProgressText
  private final String indexingProgressText;
  @Nullable @NlsContexts.ProgressText
  private final String scanningProgressText;
  @Nullable @NonNls
  private final String debugName;

  public ExternalEntityIndexableIteratorImpl(EntityReference<?> entityReference,
                                             IndexingSourceRootHolder roots) {
    this(entityReference, roots, null, null, null);
  }

  public ExternalEntityIndexableIteratorImpl(@NotNull EntityReference<?> entityReference,
                                             @NotNull IndexingSourceRootHolder roots,
                                             @Nullable @NlsContexts.ProgressText String scanningProgressText,
                                             @Nullable @NlsContexts.ProgressText String indexingProgressText,
                                             @Nullable @NonNls String debugName) {
    this.entityReference = entityReference;
    this.roots = roots.immutableCopyOf();
    this.indexingProgressText = indexingProgressText;
    this.scanningProgressText = scanningProgressText;
    this.debugName = debugName;
  }

  @NonNls
  @Override
  public String getDebugName() {
    return debugName != null
           ? debugName
           : "External roots from entity (" + roots.getRootsDebugStr() + ")";
  }

  @NlsContexts.ProgressText
  @Override
  public String getIndexingProgressText() {
    return indexingProgressText != null ? indexingProgressText : ExternalEntityIndexableIterator.super.getIndexingProgressText();
  }

  @NlsContexts.ProgressText
  @Override
  public String getRootsScanningProgressText() {
    return scanningProgressText != null ? scanningProgressText : ExternalEntityIndexableIterator.super.getRootsScanningProgressText();
  }

  @NotNull
  @Override
  public ExternalEntityOrigin getOrigin() {
    return new ExternalEntityOriginImpl(entityReference, roots);
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
