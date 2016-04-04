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
package com.intellij.dvcs.ui;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
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
    return !ContainerUtil.exists(grouped.entrySet(), new Condition<Map.Entry<Repo, Collection<Hash>>>() {
      @Override
      public boolean value(Map.Entry<Repo, Collection<Hash>> entry) {
        return entry.getValue().size() != 1;
      }
    });
  }

  @Nullable
  private Map<Repo, VcsFullCommitDetails> convertToSingleElementMap(@NotNull MultiMap<Repo, VcsFullCommitDetails> groupedCommits) {
    Map<Repo, VcsFullCommitDetails> map = ContainerUtil.newHashMap();
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
