// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class DvcsBranchUtil {
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

  @NotNull
  public static List<Change> swapRevisions(@NotNull List<? extends Change> changes) {
    return ContainerUtil.map(changes, change -> {
      ContentRevision beforeRevision = change.getBeforeRevision();
      ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision == null || afterRevision == null) return new Change(afterRevision, beforeRevision);
      return new Change(afterRevision, beforeRevision, change.getFileStatus());
    });
  }
}
