// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class VcsLogOneCommitPerRepoAction<Repo extends Repository> extends VcsLogAction<Repo> {

  @Override
  protected void actionPerformed(@NotNull Project project, @NotNull MultiMap<Repo, VcsFullCommitDetails> grouped) {
    Map<Repo, VcsFullCommitDetails> singleElementMap = convertToSingleElementMap(grouped);
    assert singleElementMap != null;
    actionPerformed(project, singleElementMap);
  }

  @Override
  protected boolean isEnabled(@NotNull MultiMap<Repo, Hash> grouped) {
    return allValuesAreSingletons(grouped);
  }

  protected abstract void actionPerformed(@NotNull Project project, @NotNull Map<Repo, VcsFullCommitDetails> commits);

  private boolean allValuesAreSingletons(@NotNull MultiMap<Repo, Hash> grouped) {
    return ContainerUtil.and(grouped.entrySet(), entry -> entry.getValue().size() == 1);
  }

  private @Nullable Map<Repo, VcsFullCommitDetails> convertToSingleElementMap(@NotNull MultiMap<Repo, VcsFullCommitDetails> groupedCommits) {
    Map<Repo, VcsFullCommitDetails> map = new HashMap<>();
    for (Map.Entry<Repo, Collection<VcsFullCommitDetails>> entry : groupedCommits.entrySet()) {
      Collection<VcsFullCommitDetails> commits = entry.getValue();
      if (commits.size() != 1) {
        return null;
      }
      map.put(entry.getKey(), commits.iterator().next());
    }
    return map;
  }
}
