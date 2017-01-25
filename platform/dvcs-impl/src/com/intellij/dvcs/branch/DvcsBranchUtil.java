/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class DvcsBranchUtil {


  @Nullable
  public static <T extends DvcsBranchInfo> T find(@Nullable final Collection<T> branches,
                                                  @Nullable Repository repository,
                                                  @NotNull String sourceBranch) {
    if (branches == null) return null;
    return ContainerUtil.find(branches, targetInfo -> repoAndSourceAreEqual(repository, sourceBranch, targetInfo));
  }

  private static boolean repoAndSourceAreEqual(@Nullable Repository repository,
                                               @NotNull String sourceBranch,
                                               @NotNull DvcsBranchInfo targetInfo) {
    return getPathFor(repository).equals(targetInfo.repoPath) && StringUtil.equals(targetInfo.sourceName, sourceBranch);
  }

  @NotNull
  public static String getPathFor(@Nullable Repository repository) {
    return repository == null ? "" : repository.getRoot().getPath();
  }
}
