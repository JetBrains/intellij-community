// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.util;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class CommitCompareInfo {

  private static final Logger LOG = Logger.getInstance(CommitCompareInfo.class);

  private final Map<Repository, Pair<List<VcsFullCommitDetails>, List<VcsFullCommitDetails>>> myInfo = new HashMap<>();
  private final Map<Repository, Collection<Change>> myTotalDiff = new HashMap<>();
  private final InfoType myInfoType;

  public CommitCompareInfo() {
    this(InfoType.BOTH);
  }

  public CommitCompareInfo(@NotNull InfoType infoType) {
    myInfoType = infoType;
  }

  public void put(@NotNull Repository repository, @NotNull List<? extends VcsFullCommitDetails> headToBranch, @NotNull List<? extends VcsFullCommitDetails> branchToHead) {
    //noinspection unchecked
    myInfo.put(repository, (Pair)Couple.of(headToBranch, branchToHead));
  }

  public void putTotalDiff(@NotNull Repository repository, @NotNull Collection<Change> totalDiff) {
    myTotalDiff.put(repository, totalDiff);
  }

  public @NotNull List<VcsFullCommitDetails> getHeadToBranchCommits(@NotNull Repository repo) {
    return getCompareInfo(repo).getFirst();
  }

  public @NotNull List<VcsFullCommitDetails> getBranchToHeadCommits(@NotNull Repository repo) {
    return getCompareInfo(repo).getSecond();
  }

  private @NotNull Pair<List<VcsFullCommitDetails>, List<VcsFullCommitDetails>> getCompareInfo(@NotNull Repository repo) {
    Pair<List<VcsFullCommitDetails>, List<VcsFullCommitDetails>> pair = myInfo.get(repo);
    if (pair == null) {
      LOG.error("Compare info not found for repository " + repo);
      return Pair.create(Collections.emptyList(), Collections.emptyList());
    }
    return pair;
  }

  public @NotNull Collection<Repository> getRepositories() {
    return myTotalDiff.keySet();
  }

  public boolean isEmpty() {
    return myInfo.isEmpty();
  }

  public InfoType getInfoType() {
    return myInfoType;
  }

  public @NotNull List<Change> getTotalDiff() {
    List<Change> changes = new ArrayList<>();
    for (Collection<Change> changeCollection : myTotalDiff.values()) {
      changes.addAll(changeCollection);
    }
    return changes;
  }

  protected void updateTotalDiff(@NotNull Map<Repository, Collection<Change>> newDiff) {
    myTotalDiff.clear();
    myTotalDiff.putAll(newDiff);
  }

  public enum InfoType {
    BOTH, HEAD_TO_BRANCH, BRANCH_TO_HEAD
  }
}
