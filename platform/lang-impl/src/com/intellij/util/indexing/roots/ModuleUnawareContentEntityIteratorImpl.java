// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.customizingIteration.ModuleUnawareContentEntityIterator;
import com.intellij.util.indexing.roots.origin.ModuleUnawareContentEntityOrigin;
import com.intellij.util.indexing.roots.origin.ModuleUnawareContentEntityOriginImpl;
import com.intellij.workspaceModel.storage.EntityReference;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.util.indexing.roots.ExternalEntityIndexableIteratorImpl.getRootsDebugStr;

public class ModuleUnawareContentEntityIteratorImpl implements ModuleUnawareContentEntityIterator {

  private final EntityReference<?> entityReference;
  private final Collection<? extends VirtualFile> roots;
  @Nullable @NlsContexts.ProgressText
  private final String indexingProgressText;
  @Nullable @NlsContexts.ProgressText
  private final String scanningProgressText;
  @Nullable @NonNls
  private final String debugName;

  public ModuleUnawareContentEntityIteratorImpl(@NotNull EntityReference<?> entityReference,
                                                @NotNull Collection<? extends VirtualFile> roots) {
    this(entityReference, roots, null, null, null);
  }

  public ModuleUnawareContentEntityIteratorImpl(@NotNull EntityReference<?> entityReference,
                                                @NotNull Collection<? extends VirtualFile> roots,
                                                @Nullable @NlsContexts.ProgressText String scanningProgressText,
                                                @Nullable @NlsContexts.ProgressText String indexingProgressText,
                                                @Nullable @NonNls String debugName) {
    this.entityReference = entityReference;
    this.roots = List.copyOf(roots);
    this.indexingProgressText = indexingProgressText;
    this.scanningProgressText = scanningProgressText;
    this.debugName = debugName;
  }

  @NonNls
  @Override
  public String getDebugName() {
    return debugName != null ? debugName : "Module unaware content roots from entity (" + getRootsDebugStr(roots) + ")";
  }

  @NlsContexts.ProgressText
  @Override
  public String getIndexingProgressText() {
    return indexingProgressText != null ? indexingProgressText : ModuleUnawareContentEntityIterator.super.getIndexingProgressText();
  }

  @NlsContexts.ProgressText
  @Override
  public String getRootsScanningProgressText() {
    return scanningProgressText != null ? scanningProgressText : ModuleUnawareContentEntityIterator.super.getRootsScanningProgressText();
  }

  @NotNull
  @Override
  public ModuleUnawareContentEntityOrigin getOrigin() {
    return new ModuleUnawareContentEntityOriginImpl(entityReference, roots);
  }

  @Override
  public final boolean iterateFiles(@NotNull Project project,
                                    @NotNull ContentIterator fileIterator,
                                    @NotNull VirtualFileFilter fileFilter) {
    return IndexableFilesIterationMethods.INSTANCE.iterateRoots(project, roots, fileIterator, fileFilter, true);
  }

  @Override
  public final @NotNull Set<String> getRootUrls(@NotNull Project project) {
    return ContainerUtil.map2Set(roots, VirtualFile::getUrl);
  }
}
