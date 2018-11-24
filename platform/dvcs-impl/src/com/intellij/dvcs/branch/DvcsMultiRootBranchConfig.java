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

import com.intellij.dvcs.repo.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;

public abstract class DvcsMultiRootBranchConfig<Repo extends Repository> {
  @NotNull protected final Collection<Repo> myRepositories;

  public DvcsMultiRootBranchConfig(@NotNull Collection<Repo> repositories) {
    myRepositories = repositories;
  }

  public boolean diverged() {
    return getCurrentBranch() == null;
  }

  @Nullable
  public String getCurrentBranch() {
    return getCommonName(Repository::getCurrentBranchName);
  }

  @Nullable
  public String getCommonName(@NotNull Function<Repo, String> nameSupplier) {
    String commonBranch = null;
    for (Repo repository : myRepositories) {
      String branchName = nameSupplier.apply(repository);
      if (branchName == null) {
        return null;
      }
      // NB: if all repositories are in the rebasing state on the same branches, this branch is returned
      if (commonBranch == null) {
        commonBranch = branchName;
      }
      else if (!commonBranch.equals(branchName)) {
        return null;
      }
    }
    return commonBranch;
  }

  @Nullable
  public Repository.State getState() {
    Repository.State commonState = null;
    for (Repo repository : myRepositories) {
      Repository.State state = repository.getState();
      if (commonState == null) {
        commonState = state;
      }
      else if (!commonState.equals(state)) {
        return null;
      }
    }
    return commonState;
  }

  @NotNull
  public abstract Collection<String> getLocalBranchNames();
}
