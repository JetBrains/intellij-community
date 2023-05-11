// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.customizingIteration.ExternalEntityIndexableIterator;
import com.intellij.util.indexing.roots.origin.ExternalEntityOrigin;
import com.intellij.util.indexing.roots.origin.ExternalEntityOriginImpl;
import com.intellij.workspaceModel.storage.EntityReference;
import kotlin.collections.CollectionsKt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ExternalEntityIndexableIteratorImpl implements ExternalEntityIndexableIterator {
  private final EntityReference<?> entityReference;
  private final Collection<? extends VirtualFile> roots;
  private final Collection<? extends VirtualFile> sourceRoots;
  @Nullable @NlsContexts.ProgressText
  private final String indexingProgressText;
  @Nullable @NlsContexts.ProgressText
  private final String scanningProgressText;
  @Nullable @NonNls
  private final String debugName;

  public ExternalEntityIndexableIteratorImpl(EntityReference<?> entityReference,
                                             Collection<? extends VirtualFile> roots,
                                             Collection<? extends VirtualFile> sourceRoots) {
    this(entityReference, roots, sourceRoots, null, null, null);
  }

  public ExternalEntityIndexableIteratorImpl(@NotNull EntityReference<?> entityReference,
                                             @NotNull Collection<? extends VirtualFile> roots,
                                             @NotNull Collection<? extends VirtualFile> sourceRoots,
                                             @Nullable @NlsContexts.ProgressText String scanningProgressText,
                                             @Nullable @NlsContexts.ProgressText String indexingProgressText,
                                             @Nullable @NonNls String debugName) {
    this.entityReference = entityReference;
    this.roots = List.copyOf(roots);
    this.sourceRoots = List.copyOf(sourceRoots);
    this.indexingProgressText = indexingProgressText;
    this.scanningProgressText = scanningProgressText;
    this.debugName = debugName;
  }

  @NonNls
  @Override
  public String getDebugName() {
    return debugName != null
           ? debugName
           : "External roots from entity (roots=" + getRootsDebugStr(roots) + ", source roots=" + getRootsDebugStr(sourceRoots) + ")";
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
    return new ExternalEntityOriginImpl(entityReference, roots, sourceRoots);
  }

  @Override
  public final boolean iterateFiles(@NotNull Project project,
                                    @NotNull ContentIterator fileIterator,
                                    @NotNull VirtualFileFilter fileFilter) {
    return IndexableFilesIterationMethods.INSTANCE.iterateRoots(project, getAllRoots(), fileIterator, fileFilter, true);
  }

  @NotNull
  private Collection<VirtualFile> getAllRoots(){
    ArrayList<VirtualFile> allRoots = new ArrayList<>(roots);
    allRoots.addAll(sourceRoots);
    return allRoots;
  }

  @Override
  public final @NotNull Set<String> getRootUrls(@NotNull Project project) {
    return ContainerUtil.map2Set(getAllRoots(), VirtualFile::getUrl);
  }

  @NonNls
  @NotNull
  public static String getRootsDebugStr(Collection<? extends VirtualFile> files) {
    if (files.isEmpty()) {
      return "empty";
    }
    return CollectionsKt.joinToString(files, ", ", "", "", 3, "...", file -> file.getName());
  }
}
