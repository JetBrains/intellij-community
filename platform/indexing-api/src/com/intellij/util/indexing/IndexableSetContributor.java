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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public abstract class IndexableSetContributor implements IndexedRootsProvider {
  
  protected static final Set<VirtualFile> EMPTY_FILE_SET = Collections.emptySet();
  private static final Logger LOG = Logger.getInstance(IndexableSetContributor.class);

  @Override
  public final Set<String> getRootsToIndex() {
    return ContainerUtil.map2Set(getAdditionalRootsToIndex(), new NotNullFunction<VirtualFile, String>() {
      @NotNull
      @Override
      public String fun(VirtualFile virtualFile) {
        return virtualFile.getUrl();
      }
    });
  }

  @NotNull
  public static Set<VirtualFile> getProjectRootsToIndex(IndexedRootsProvider provider, Project project) {
    if (provider instanceof IndexableSetContributor) {
      IndexableSetContributor contributor = (IndexableSetContributor)provider;
      Set<VirtualFile> roots = contributor.getAdditionalProjectRootsToIndex(project);
      return filterOutNulls(contributor, "getAdditionalProjectRootsToIndex(Project)", roots);
    }
    return EMPTY_FILE_SET;
  }

  @NotNull
  public static Set<VirtualFile> getRootsToIndex(IndexedRootsProvider provider) {
    if (provider instanceof IndexableSetContributor) {
      IndexableSetContributor contributor = (IndexableSetContributor)provider;
      Set<VirtualFile> roots = contributor.getAdditionalRootsToIndex();
      return filterOutNulls(contributor, "getAdditionalRootsToIndex()", roots);
    }

    final HashSet<VirtualFile> result = new HashSet<VirtualFile>();
    for (String url : provider.getRootsToIndex()) {
      ContainerUtil.addIfNotNull(VirtualFileManager.getInstance().findFileByUrl(url), result);
    }

    return result;
  }

  /**
   * @return an additional project-dependent set of {@link VirtualFile} instances to index,
   *         the returned set should not contain nulls
   */
  @NotNull
  public Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
    return EMPTY_FILE_SET;
  }

  /**
   * @return an additional project-independent set of {@link VirtualFile} instances to index,
   *         the returned set should not contain nulls
   */
  @NotNull
  public abstract Set<VirtualFile> getAdditionalRootsToIndex();

  @NotNull
  private static Set<VirtualFile> filterOutNulls(@NotNull IndexableSetContributor contributor,
                                                 @NotNull String methodInfo,
                                                 @NotNull Set<VirtualFile> roots) {
    for (VirtualFile root : roots) {
      if (root == null) {
        LOG.error("Please fix " + contributor.getClass().getName() + "#" + methodInfo + ".\n" +
                  "The returned set is not expected to contain nulls, but it is " + roots);
        Set<VirtualFile> result = ContainerUtil.newHashSet(roots.size());
        ContainerUtil.addAllNotNull(result, roots);
        return result;
      }
    }
    return roots;
  }
}
