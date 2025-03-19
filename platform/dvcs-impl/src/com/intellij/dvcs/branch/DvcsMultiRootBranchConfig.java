// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.MultiRootBranches;
import com.intellij.dvcs.repo.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class DvcsMultiRootBranchConfig<Repo extends Repository> {
  protected final @NotNull Collection<? extends Repo> myRepositories;

  public DvcsMultiRootBranchConfig(@NotNull Collection<? extends Repo> repositories) {
    myRepositories = repositories;
  }

  public boolean diverged() {
    return getCurrentBranch() == null;
  }

  public @Nullable String getCurrentBranch() {
    return MultiRootBranches.getCommonCurrentBranch(myRepositories);
  }

  public abstract @NotNull Collection<String> getLocalBranchNames();
}
