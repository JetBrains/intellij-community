// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents a set of files which should be indexed additionally to a default ones.
 * <br>
 * Files provided by {@link IndexableSetContributor} will be indexed (or ensured up to date) on project loading and
 * {@link FileBasedIndex} automatically rebuilds indexes for these files when they are going to be changed.
 */
@ApiStatus.OverrideOnly
public abstract class IndexableSetContributor {

  public static final ExtensionPointName<IndexableSetContributor> EP_NAME = new ExtensionPointName<>("com.intellij.indexedRootsProvider");
  private static final Logger LOG = Logger.getInstance(IndexableSetContributor.class);

  @NotNull
  public static Set<VirtualFile> getProjectRootsToIndex(@NotNull IndexableSetContributor contributor, @NotNull Project project) {
    Set<VirtualFile> roots = contributor.getAdditionalProjectRootsToIndex(project);
    return filterOutNulls(contributor, "getAdditionalProjectRootsToIndex(Project)", roots);
  }

  @NotNull
  public static Set<VirtualFile> getRootsToIndex(@NotNull IndexableSetContributor contributor) {
    Set<VirtualFile> roots = contributor.getAdditionalRootsToIndex();
    return filterOutNulls(contributor, "getAdditionalRootsToIndex()", roots);
  }

  @ApiStatus.Experimental
  public boolean acceptFile(@NotNull VirtualFile file, @NotNull VirtualFile root, @Nullable Project project) {
    return true;
  }

  /**
   * @return an additional project-dependent set of {@link VirtualFile} instances to index,
   *         the returned set should not contain {@code null} files or invalid files.
   */
  @NotNull
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
    return Collections.emptySet();
  }

  /**
   * @return an additional project-independent set of {@link VirtualFile} instances to index,
   *         the returned set should not contain {@code null} files or invalid files.
   */
  @NotNull
  public abstract Set<VirtualFile> getAdditionalRootsToIndex();

  @NotNull
  private static Set<VirtualFile> filterOutNulls(@NotNull IndexableSetContributor contributor,
                                                 @NotNull String methodInfo,
                                                 @NotNull Set<VirtualFile> roots) {
    for (VirtualFile root : roots) {
      if (root == null || !root.isValid()) {
        LOG.error(PluginException.createByClass("Please fix " + contributor.getClass().getName() + "#" + methodInfo + ".\n" +
                                                (root == null ? "The returned set is not expected to contain nulls, but it is " + roots
                                                              : "Invalid file returned: " + root), null, contributor.getClass()));
        return new LinkedHashSet<>(ContainerUtil.filter(roots, virtualFile -> virtualFile != null && virtualFile.isValid()));
      }
    }
    return roots;
  }
}
