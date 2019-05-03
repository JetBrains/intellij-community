// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
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

  /**
   * @return an additional project-dependent set of {@link VirtualFile} instances to index,
   *         the returned set should not contain nulls or invalid files
   */
  @NotNull
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
    return Collections.emptySet();
  }

  /**
   * @return an additional project-independent set of {@link VirtualFile} instances to index,
   *         the returned set should not contain nulls or invalid files
   */
  @NotNull
  public abstract Set<VirtualFile> getAdditionalRootsToIndex();

  @NotNull
  private static Set<VirtualFile> filterOutNulls(@NotNull IndexableSetContributor contributor,
                                                 @NotNull String methodInfo,
                                                 @NotNull Set<VirtualFile> roots) {
    for (VirtualFile root : roots) {
      if (root == null || !root.isValid()) {
        LOG.error("Please fix " + contributor.getClass().getName() + "#" + methodInfo + ".\n" +
                  (root == null ? "The returned set is not expected to contain nulls, but it is " + roots
                                : "Invalid file returned: " + root));
        return new LinkedHashSet<>(ContainerUtil.filter(roots, virtualFile -> virtualFile != null && virtualFile.isValid()));
      }
    }
    return roots;
  }
}
