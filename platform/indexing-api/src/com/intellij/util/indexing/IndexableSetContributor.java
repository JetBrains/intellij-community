/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
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
        return ContainerUtil.newLinkedHashSet(ContainerUtil.filter(roots, virtualFile -> virtualFile != null && virtualFile.isValid()));
      }
    }
    return roots;
  }
}
