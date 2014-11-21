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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsTaskHandler;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

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
  public boolean isEnabled(@Nullable Project project) {
    return project != null && !project.isDisposed() && !myRepositoryManager.getRepositories().isEmpty();
  }

  @Override
  public TaskInfo startNewTask(@NotNull final String taskName) {
    List<R> repositories = myRepositoryManager.getRepositories();
    List<R> problems = ContainerUtil.filter(repositories, new Condition<R>() {
      @Override
      public boolean value(R repository) {
        return hasBranch(repository, taskName);
      }
    });
    MultiMap<String, String> map = new MultiMap<String, String>();
    if (!problems.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode() ||
          Messages.showDialog(myProject,
                              "<html>The following repositories already have specified " + myBranchType + "<b>" + taskName + "</b>:<br>" +
                              StringUtil.join(problems, "<br>") + ".<br>" +
                              "Do you want to checkout existing " + myBranchType + "?", myBranchType + " Already Exists",
                              new String[]{Messages.YES_BUTTON, Messages.NO_BUTTON}, 0,
                              Messages.getWarningIcon(), new DialogWrapper.PropertyDoNotAskOption("git.checkout.existing.branch")) == 0) {
        checkout(taskName, problems, null);
        fillMap(taskName, problems, map);
      }
    }
    repositories.removeAll(problems);
    if (!repositories.isEmpty()) {
      checkoutAsNewBranch(taskName, repositories);
    }

    fillMap(taskName, repositories, map);
    return new TaskInfo(map);
  }

  private static <R extends Repository> void fillMap(String taskName, List<R> repositories, MultiMap<String, String> map) {
    for (R repository : repositories) {
      map.putValue(taskName, repository.getPresentableUrl());
    }
  }

  @Override
  public void switchToTask(@NotNull TaskInfo taskInfo, @Nullable Runnable invokeAfter) {
    for (final String branchName : taskInfo.branches.keySet()) {
      List<R> repositories = getRepositories(taskInfo.branches.get(branchName));
      List<R> notFound = ContainerUtil.filter(repositories, new Condition<R>() {
        @Override
        public boolean value(R repository) {
          return !hasBranch(repository, branchName);
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
  }

  @Override
  public void closeTask(@NotNull final TaskInfo taskInfo, @NotNull TaskInfo original) {
    Set<String> branches = original.branches.keySet();
    final AtomicInteger counter = new AtomicInteger(branches.size());
    for (final String originalBranch : branches) {
      checkout(originalBranch, getRepositories(original.branches.get(originalBranch)), new Runnable() {
        @Override
        public void run() {
          if (counter.decrementAndGet() == 0) {
            merge(taskInfo);
          }
        }
      });
    }
  }

  private void merge(@NotNull TaskInfo taskInfo) {
    for (String featureBranch : taskInfo.branches.keySet()) {
      mergeAndClose(featureBranch, getRepositories(taskInfo.branches.get(featureBranch)));
    }
  }

  @Override
  @NotNull
  public TaskInfo getActiveTask() {
    List<R> repositories = myRepositoryManager.getRepositories();

    MultiMap<String, String> branches = new MultiMap<String, String>();
    for (R repository : repositories) {
      String branchName = repository.getCurrentBranchName();
      if (branchName != null) {
        branches.putValue(branchName, repository.getPresentableUrl());
      }
    }
    return new TaskInfo(branches);
  }

  @Override
  public TaskInfo[] getCurrentTasks() {
    List<R> repositories = myRepositoryManager.getRepositories();
    final List<String> names = ContainerUtil.map(repositories, new Function<R, String>() {
      @Override
      public String fun(R repository) {
        return repository.getPresentableUrl();
      }
    });
    Collection<String> branches = getCommonBranchNames(repositories);
    return ContainerUtil.map2Array(branches, TaskInfo.class, new Function<String, TaskInfo>() {
      @Override
      public TaskInfo fun(String branchName) {
        MultiMap<String, String> map = new MultiMap<String, String>();
        map.put(branchName, names);
        return new TaskInfo(map);
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

  @NotNull
  protected abstract Collection<String> getCommonBranchNames(@NotNull List<R> repositories);

  protected abstract void mergeAndClose(@NotNull String branch, @NotNull List<R> repositories);

  protected abstract boolean hasBranch(@NotNull R repository, @NotNull String name);
}
