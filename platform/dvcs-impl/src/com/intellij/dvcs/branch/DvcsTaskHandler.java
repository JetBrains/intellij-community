/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class DvcsTaskHandler<R extends Repository> extends VcsTaskHandler {

  @NotNull private final AbstractRepositoryManager<R> myRepositoryManager;
  @NotNull private final Project myProject;
  @NotNull private final String myBranchType;

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
  public TaskInfo startNewTask(@NotNull final String taskName) {
    List<R> repositories = myRepositoryManager.getRepositories();
    List<R> problems = ContainerUtil.filter(repositories, new Condition<R>() {
      @Override
      public boolean value(R repository) {
        return hasBranch(repository, new TaskInfo(taskName, Collections.emptyList()));
      }
    });
    List<R> map = new ArrayList<>();
    if (!problems.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode() ||
          Messages.showDialog(myProject,
                              "<html>The following repositories already have specified " + myBranchType + "<b>" + taskName + "</b>:<br>" +
                              StringUtil.join(problems, "<br>") + ".<br>" +
                              "Do you want to checkout existing " + myBranchType + "?", StringUtil.capitalize(myBranchType) + " Already Exists",
                              new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 0,
                              Messages.getWarningIcon()) == 0) {
        checkout(taskName, problems, null);
        map.addAll(problems);
      }
    }
    repositories.removeAll(problems);
    if (!repositories.isEmpty()) {
      checkoutAsNewBranch(taskName, repositories);
    }

    map.addAll(repositories);
    return new TaskInfo(taskName, ContainerUtil.map(map, new Function<R, String>() {
      @Override
      public String fun(R r) {
        return r.getPresentableUrl();
      }
    }));
  }

  @Override
  public void switchToTask(@NotNull TaskInfo taskInfo, @Nullable Runnable invokeAfter) {
    final String branchName = taskInfo.getName();
    List<R> repositories = getRepositories(taskInfo.getRepositories());
    List<R> notFound = ContainerUtil.filter(repositories, new Condition<R>() {
      @Override
      public boolean value(R repository) {
        return !hasBranch(repository, taskInfo);
      }
    });
    if (!notFound.isEmpty()) {
      checkoutAsNewBranch(branchName, notFound);
    }
    repositories.removeAll(notFound);
    if (!repositories.isEmpty()) {
      checkout(branchName, repositories, invokeAfter);
    }
  }

  @Override
  public void closeTask(@NotNull final TaskInfo taskInfo, @NotNull TaskInfo original) {
    checkout(original.getName(), getRepositories(original.getRepositories()), new Runnable() {
      @Override
      public void run() {
        mergeAndClose(taskInfo.getName(), getRepositories(taskInfo.getRepositories()));
      }
    });
  }

  @Override
  public boolean isSyncEnabled() {
    return myRepositoryManager.isSyncEnabled();
  }

  @NotNull
  @Override
  public TaskInfo[] getCurrentTasks() {
    List<R> repositories = myRepositoryManager.getRepositories();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    FactoryMap<String, TaskInfo> tasks = new FactoryMap<String, TaskInfo>() {
      @Nullable
      @Override
      protected TaskInfo create(String key) {
        return new TaskInfo(key, new ArrayList<>());
      }
    };
    for (R repository : repositories) {
      String branch = getActiveBranch(repository);
      if (branch != null) {
        tasks.get(branch).getRepositories().add(repository.getPresentableUrl());
      }
    }
    if (tasks.size() == 0) return new TaskInfo[0];
    if (isSyncEnabled()) {
      return new TaskInfo[] { tasks.values().iterator().next() };
    }
    else {
      return tasks.values().toArray(new TaskInfo[tasks.values().size()]);
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
    return ContainerUtil.map2Array(tasks.entrySet(), TaskInfo.class, new Function<Map.Entry<String, Collection<TaskInfo>>, TaskInfo>() {
      @Override
      public TaskInfo fun(Map.Entry<String, Collection<TaskInfo>> entry) {
        Set<String> repositories = new HashSet<>();
        boolean remote = false;
        for (TaskInfo info : entry.getValue()) {
          remote |= info.isRemote();
          repositories.addAll(info.getRepositories());
        }
        return new TaskInfo(entry.getKey(), repositories, remote);
      }
    });
  }

  @NotNull
  private List<R> getRepositories(@NotNull Collection<String> urls) {
    final List<R> repositories = myRepositoryManager.getRepositories();
    return ContainerUtil.mapNotNull(urls, new NullableFunction<String, R>() {
      @Nullable
      @Override
      public R fun(final String s) {

        return ContainerUtil.find(repositories, new Condition<R>() {
          @Override
          public boolean value(R repository) {
            return s.equals(repository.getPresentableUrl());
          }
        });
      }
    });
  }

  protected abstract void checkout(@NotNull String taskName, @NotNull List<R> repos, @Nullable Runnable callInAwtLater);

  protected abstract void checkoutAsNewBranch(@NotNull String name, @NotNull List<R> repositories);

  @Nullable
  protected abstract String getActiveBranch(R repository);

  @NotNull
  protected abstract Iterable<TaskInfo> getAllBranches(@NotNull R repository);

  protected abstract void mergeAndClose(@NotNull String branch, @NotNull List<R> repositories);

  protected abstract boolean hasBranch(@NotNull R repository, @NotNull TaskInfo name);
}
