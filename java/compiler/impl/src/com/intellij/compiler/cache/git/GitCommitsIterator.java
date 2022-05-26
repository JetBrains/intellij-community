// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.cache.git;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class GitCommitsIterator implements Iterator<String> {
  private static final Logger LOG = Logger.getInstance(GitCommitsIterator.class);

  private static final int MAX_FETCH_SIZE = 1000;
  private List<String> repositoryCommits;
  private final Project myProject;
  private int fetchedCount;
  private int currentPosition;
  private final String remote;

  public GitCommitsIterator(@NotNull Project project, @NotNull String remoteUrl) {
    myProject = project;
    fetchedCount = 0;
    remote = remoteUrl;
    fetchOldCommits();
  }

  @Override
  public boolean hasNext() {
    if (repositoryCommits.size() > 0) {
      if (currentPosition < repositoryCommits.size()) return true;
      if (fetchedCount >= MAX_FETCH_SIZE) {
        LOG.info("Exceeded fetch limit for git commits");
        return false;
      }
      fetchOldCommits(repositoryCommits.get(currentPosition - 1));
      if (repositoryCommits.size() > 0) {
        currentPosition = 0;
        return true;
      }
    }
    return false;
  }

  @Override
  public String next() {
    if (repositoryCommits.size() == 0 || currentPosition >= repositoryCommits.size()) throw new NoSuchElementException();
    String result = repositoryCommits.get(currentPosition);
    currentPosition++;
    return result;
  }

  @NotNull
  public String getRemote() {
    return remote;
  }

  private void fetchOldCommits() {
    fetchOldCommits("");
  }

  private void fetchOldCommits(String latestCommit) {
    repositoryCommits = new SmartList<>();
    repositoryCommits = GitRepositoryUtil.fetchRepositoryCommits(myProject, latestCommit);
    fetchedCount += repositoryCommits.size();
  }
}