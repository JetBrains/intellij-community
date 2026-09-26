// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OutgoingResult {
  private final @NotNull List<VcsError> myErrors;
  private final @NotNull List<? extends VcsFullCommitDetails> myCommits;

  public OutgoingResult(@NotNull List<? extends VcsFullCommitDetails> commits, @NotNull List<VcsError> errors) {
    myCommits = commits;
    myErrors = errors;
  }

  public @NotNull List<VcsError> getErrors() {
    return myErrors;
  }

  public @NotNull List<? extends VcsFullCommitDetails> getCommits() {
    return myCommits;
  }
}
