// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public final class DvcsBranchUtil {
  public static @NotNull String getPathFor(@Nullable Repository repository) {
    return repository == null ? "" : repository.getRoot().getPath();
  }

  public static @NotNull @Unmodifiable List<Change> swapRevisions(@NotNull List<? extends Change> changes) {
    return ContainerUtil.map(changes, change -> {
      ContentRevision beforeRevision = change.getBeforeRevision();
      ContentRevision afterRevision = change.getAfterRevision();
      if (beforeRevision == null || afterRevision == null) return new Change(afterRevision, beforeRevision);
      return new Change(afterRevision, beforeRevision, change.getFileStatus());
    });
  }

  public static @Nls @NotNull String shortenBranchName(@Nls @NotNull String fullBranchName) {
    return StringUtil.shortenTextWithEllipsis(fullBranchName, 100, 5);
  }
}
