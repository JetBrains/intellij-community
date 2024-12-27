// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public abstract class DvcsTaskHandler<R extends Repository> extends VcsTaskHandler {

  private final @NotNull AbstractRepositoryManager<R> myRepositoryManager;
  private final @NotNull Project myProject;
  private final @NotNull String myBranchType;

  protected DvcsTaskHandler(@NotNull AbstractRepositoryManager<R> repositoryManager, @NotNull Project project, @NotNull String branchType) {
    myRepositoryManager = repositoryManager;
    myProject = project;
    myBranchType = branchType;
  }

  @Override
  public boolean isEnabled() {
    return !myRepositoryManager.getRepositories().isEmpty();
  }

  @Override
  public TaskInfo startNewTask(final @NotNull String taskName) {
    List<R> repositories = myRepositoryManager.getRepositories();
    List<R> problems = ContainerUtil.filter(repositories,
                                            repository -> hasBranch(repository, new TaskInfo(taskName, Collections.emptyList())));
    List<R> map = new ArrayList<>();
    if (!problems.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode() ||
          MessageDialogBuilder
            .yesNo(DvcsBundle.message("dialog.title.already.exists", StringUtil.capitalize(myBranchType)),
                   DvcsBundle.message("dialog.message.following.repositories.already.have.specified",
                                      myBranchType, taskName, StringUtil.join(problems, UIUtil.BR), myBranchType))
            .icon(Messages.getWarningIcon())
            .ask(myProject)) {
        checkout(taskName, problems, null);
        map.addAll(problems);
      }
      repositories = ContainerUtil.filter(repositories, r->!problems.contains(r));
    }
    if (!repositories.isEmpty()) {
      checkoutAsNewBranch(taskName, repositories);
    }

    map.addAll(repositories);
    return new TaskInfo(taskName, ContainerUtil.map(map, r -> r.getPresentableUrl()));
  }

  @Override
  public boolean switchToTask(@NotNull TaskInfo taskInfo, @Nullable Runnable invokeAfter) {
    List<R> repositories = getRepositories(taskInfo.getRepositories());
    List<R> notFound = ContainerUtil.filter(repositories, repository -> !hasBranch(repository, taskInfo));
    final String branchName = taskInfo.getName();
    if (!notFound.isEmpty()) {
      checkoutAsNewBranch(branchName, notFound);
    }
    repositories = new ArrayList<>(ContainerUtil.subtract(repositories, notFound));
    if (!repositories.isEmpty()) {
      checkout(branchName, repositories, invokeAfter);
      return true;
    }
    return false;
  }

  @Override
  public void closeTask(final @NotNull TaskInfo taskInfo, @NotNull TaskInfo original) {
    checkout(original.getName(), getRepositories(original.getRepositories()),
             () -> mergeAndClose(taskInfo.getName(), getRepositories(taskInfo.getRepositories())));
  }

  @Override
  public boolean isSyncEnabled() {
    return myRepositoryManager.isSyncEnabled();
  }

  @Override
  public TaskInfo @NotNull [] getCurrentTasks() {
    List<R> repositories = myRepositoryManager.getRepositories();
    Map<String, TaskInfo> tasks = FactoryMap.create(key -> new TaskInfo(key, new ArrayList<>()));
    for (R repository : repositories) {
      String branch = getActiveBranch(repository);
      if (branch != null) {
        tasks.get(branch).getRepositories().add(repository.getPresentableUrl());
      }
    }
    if (tasks.isEmpty()) return new TaskInfo[0];
    if (isSyncEnabled()) {
      return new TaskInfo[]{tasks.values().iterator().next()};
    }
    else {
      return tasks.values().toArray(new TaskInfo[0]);
    }
  }

  @Override
  public TaskInfo[] getAllExistingTasks() {
    List<R> repositories = myRepositoryManager.getRepositories();
    MultiMap<String, TaskInfo> tasks = new MultiMap<>();
    for (R repository : repositories) {
      for (TaskInfo branch : getAllBranches(repository)) {
        tasks.putValue(branch.getName(), branch);
      }
    }
    return ContainerUtil.map2Array(tasks.entrySet(), TaskInfo.class, entry -> {
      Set<String> repositories1 = new HashSet<>();
      boolean remote = false;
      for (TaskInfo info : entry.getValue()) {
        remote |= info.isRemote();
        repositories1.addAll(info.getRepositories());
      }
      return new TaskInfo(entry.getKey(), repositories1, remote);
    });
  }

  private @NotNull @Unmodifiable List<R> getRepositories(@NotNull Collection<String> urls) {
    final List<R> repositories = myRepositoryManager.getRepositories();
    return ContainerUtil.mapNotNull(urls, s -> ContainerUtil.find(repositories, repository -> s.equals(repository.getPresentableUrl())));
  }

  protected abstract void checkout(@NotNull String taskName, @NotNull List<? extends R> repos, @Nullable Runnable callInAwtLater);

  protected abstract void checkoutAsNewBranch(@NotNull String name, @NotNull List<? extends R> repositories);

  protected abstract @Nullable String getActiveBranch(R repository);

  protected abstract @NotNull Iterable<TaskInfo> getAllBranches(@NotNull R repository);

  protected abstract void mergeAndClose(@NotNull String branch, @NotNull List<? extends R> repositories);

  protected abstract boolean hasBranch(@NotNull R repository, @NotNull TaskInfo name);
}
