// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.util;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class LocalCommitCompareInfo extends CommitCompareInfo {
  public abstract void copyChangesFromBranch(@NotNull List<? extends Change> changes,
                                             boolean swapSides) throws VcsException;
}
