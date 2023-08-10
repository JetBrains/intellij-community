// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.roots.SyntheticLibraryIndexableFilesIteratorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;

class SyntheticLibraryDescriptor {
  @NotNull
  public final Class<? extends AdditionalLibraryRootsProvider> providerClass;
  @NotNull
  public final AdditionalLibraryRootsProvider provider;
  @Nullable
  public final String comparisonId;
  @NotNull
  public final SyntheticLibrary library;
  @Nullable @NlsSafe
  public final String presentableLibraryName;
  @NotNull
  @NlsSafe
  public final String debugLibraryName;
  @NotNull
  public final Set<VirtualFile> sourceRoots;
  @NotNull
  public final Set<VirtualFile> binaryRoots;
  @NotNull
  public final Set<VirtualFile> excludedRoots;
  public final boolean hasExcludeFileCondition;

  SyntheticLibraryDescriptor(@NotNull SyntheticLibrary library,
                             @NotNull AdditionalLibraryRootsProvider provider) {
    this(provider,
         library,
         library instanceof ItemPresentation ? ((ItemPresentation)library).getPresentableText() : null,
         library.toString(),
         Set.copyOf(library.getSourceRoots()),
         Set.copyOf(library.getBinaryRoots()),
         Set.copyOf(library.getExcludedRoots()),
         library.getExcludeFileCondition());
  }

  private SyntheticLibraryDescriptor(@NotNull AdditionalLibraryRootsProvider provider,
                                     @NotNull SyntheticLibrary library,
                                     @Nullable String presentableLibraryName,
                                     @NotNull String debugLibraryName,
                                     @NotNull Set<VirtualFile> sourceRoots,
                                     @NotNull Set<VirtualFile> binaryRoots,
                                     @NotNull Set<VirtualFile> excludedRoots,
                                     @Nullable Condition<VirtualFile> excludeFileCondition) {
    this.provider = provider;
    this.providerClass = provider.getClass();
    this.comparisonId = library.getComparisonId();
    this.library = library;
    this.presentableLibraryName = presentableLibraryName;
    this.debugLibraryName = debugLibraryName;
    this.sourceRoots = sourceRoots;
    this.binaryRoots = binaryRoots;
    this.excludedRoots = excludedRoots;
    this.hasExcludeFileCondition = excludeFileCondition != null;
  }

  @NotNull
  public SyntheticLibraryIndexableFilesIteratorImpl toIndexableIterator() {
    return new SyntheticLibraryIndexableFilesIteratorImpl(presentableLibraryName, library, getAllRoots());
  }

  @Nullable
  public SyntheticLibraryDescriptor getLibForIncrementalRescanning(@Nullable Collection<? extends SyntheticLibraryDescriptor> before) {
    if (before == null || comparisonId == null || hasExcludeFileCondition) return null;
    return ContainerUtil.find(before, lib -> comparisonId.equals(lib.comparisonId));
  }

  @NotNull
  @Unmodifiable
  public Set<VirtualFile> getAllRoots() {
    return ContainerUtil.union(sourceRoots, binaryRoots);
  }

  public final boolean contains(@NotNull VirtualFile file) {
    return contains(file, true, true);
  }

  public boolean contains(@NotNull VirtualFile file, boolean includeSources, boolean includeBinaries) {
    return isUnderRoots(file, includeSources, includeBinaries) && !VfsUtilCore.isUnder(file, excludedRoots);
  }

  private boolean isUnderRoots(@NotNull VirtualFile file, boolean includeSources, boolean includeBinaries) {
    if (includeSources && VfsUtilCore.isUnder(file, sourceRoots)) return true;
    if (includeBinaries && VfsUtilCore.isUnder(file, binaryRoots)) return true;
    return false;
  }

  @Override
  public String toString() {
    return "SyntheticLibraryDescriptor{" +
           "providerClass=" + providerClass.getSimpleName() +
           ", sourceRoots=" + sourceRoots +
           ", binaryRoots=" + binaryRoots +
           ", excludedRoots=" + excludedRoots +
           ", hasExcludeFileCondition=" + hasExcludeFileCondition + '}';
  }
}
