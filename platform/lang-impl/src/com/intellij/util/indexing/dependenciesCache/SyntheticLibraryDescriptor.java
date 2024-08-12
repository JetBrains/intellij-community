// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

final class SyntheticLibraryDescriptor {
  public final @NotNull Class<? extends AdditionalLibraryRootsProvider> providerClass;
  public final @NotNull AdditionalLibraryRootsProvider provider;
  public final @Nullable String comparisonId;
  public final @NotNull SyntheticLibrary library;
  public final @Nullable @NlsSafe String presentableLibraryName;
  public final @NotNull @NlsSafe String debugLibraryName;
  public final @NotNull Set<VirtualFile> sourceRoots;
  public final @NotNull Set<VirtualFile> binaryRoots;
  public final @NotNull Set<VirtualFile> excludedRoots;
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

  public @NotNull SyntheticLibraryIndexableFilesIteratorImpl toIndexableIterator() {
    return new SyntheticLibraryIndexableFilesIteratorImpl(presentableLibraryName, library, getAllRoots());
  }

  public @Nullable SyntheticLibraryDescriptor getLibForIncrementalRescanning(@Nullable Collection<? extends SyntheticLibraryDescriptor> before) {
    if (before == null || comparisonId == null || hasExcludeFileCondition) return null;
    return ContainerUtil.find(before, lib -> comparisonId.equals(lib.comparisonId));
  }

  public @NotNull @Unmodifiable Set<VirtualFile> getAllRoots() {
    return ContainerUtil.union(sourceRoots, binaryRoots);
  }

  public boolean contains(@NotNull VirtualFile file) {
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
