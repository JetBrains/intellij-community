// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependenciesCache;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexableSetContributor;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.IndexableSetContributorFilesIterator;
import com.intellij.util.indexing.roots.builders.IndexableSetContributorFilesIteratorBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class IndexableSetContributorDescriptor {

  public final @Nullable @NlsSafe String presentableText;
  public final @NotNull @NlsSafe String debugName;
  public final @NotNull Set<VirtualFile> projectRoots;
  public final @NotNull Set<VirtualFile> applicationRoots;
  public final @NotNull IndexableSetContributor contributor;

  IndexableSetContributorDescriptor(@NotNull IndexableSetContributor contributor, @NotNull Project project) {
    this.presentableText = (contributor instanceof ItemPresentation) ? ((ItemPresentation)contributor).getPresentableText() : null;
    this.debugName = contributor.getDebugName();
    this.projectRoots = Set.copyOf(contributor.getAdditionalProjectRootsToIndex(project));
    this.applicationRoots = Set.copyOf(contributor.getAdditionalRootsToIndex());
    this.contributor = contributor;
  }

  public @NotNull List<IndexableFilesIterator> toIndexableIterators() {
    return Arrays.asList(new IndexableSetContributorFilesIterator(presentableText, debugName, true, projectRoots, contributor),
                         new IndexableSetContributorFilesIterator(presentableText, debugName, false, applicationRoots, contributor));
  }

  @Override
  public String toString() {
    return debugName + ": " + projectRoots + ", " + applicationRoots;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IndexableSetContributorDescriptor that = (IndexableSetContributorDescriptor)o;
    return Objects.equals(presentableText, that.presentableText) &&
           debugName.equals(that.debugName) &&
           projectRoots.equals(that.projectRoots) &&
           applicationRoots.equals(that.applicationRoots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(presentableText, debugName, projectRoots, applicationRoots);
  }

  public @NotNull List<IndexableSetContributorFilesIteratorBuilder> toIteratorBuilders() {
    return Arrays.asList(new IndexableSetContributorFilesIteratorBuilder(presentableText, debugName, projectRoots, true, contributor),
                         new IndexableSetContributorFilesIteratorBuilder(presentableText, debugName, applicationRoots, false, contributor));
  }

  public @NotNull IndexableSetContributorFilesIteratorBuilder toIteratorBuilderWithRoots(@NotNull Set<? extends VirtualFile> roots, boolean projectAware) {
    return new IndexableSetContributorFilesIteratorBuilder(presentableText, debugName, roots, projectAware, contributor);
  }

  public static @NotNull List<IndexableSetContributorDescriptor> collectDescriptors(@NotNull Project project) {
    return ContainerUtil.map(IndexableSetContributor.EP_NAME.getExtensionList(),
                             contributor -> new IndexableSetContributorDescriptor(contributor, project));
  }
}
