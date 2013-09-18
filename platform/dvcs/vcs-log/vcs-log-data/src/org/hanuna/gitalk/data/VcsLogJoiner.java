/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.hanuna.gitalk.data;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.TimeCommitParents;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Stanislav Erokhin
 * @author Kirill Likhodedov
 */
public class VcsLogJoiner {

  /**
   *
   *
   * @param savedLog       currently available part of the log.
   * @param previousRefs   references saved from the previous refresh.
   * @param firstBlock     the first n commits read from the VCS.
   * @param newRefs        all references (branches) of the repository.
   * @return Total saved log with new commits properly attached to it.
   */
  @NotNull
  public List<TimeCommitParents> addCommits(@NotNull List<TimeCommitParents> savedLog,
                                            @NotNull Collection<VcsRef> previousRefs,
                                            @NotNull List<? extends TimeCommitParents> firstBlock,
                                            @NotNull Collection<VcsRef> newRefs) {
    int unsafeBlockSize = getFirstSafeIndex(savedLog, firstBlock, newRefs);
    if (unsafeBlockSize == -1) { // firstBlock not enough
      //TODO
      throw new IllegalStateException();
    }

    List<TimeCommitParents> unsafePartSavedLog = new ArrayList<TimeCommitParents>(savedLog.subList(0, unsafeBlockSize));
    Set<TimeCommitParents> allNewsCommits = getAllNewCommits(unsafePartSavedLog, firstBlock);
    unsafePartSavedLog = new NewCommitIntegrator(unsafePartSavedLog, allNewsCommits).getResultList();

    return ContainerUtil.concat(unsafePartSavedLog, savedLog.subList(unsafeBlockSize, savedLog.size()));
  }

  /**
   *
   * @param savedLog       currently available part of the log.
   * @param firstBlock     the first n commits read from the VCS.
   * @param refs           all references (branches) of the repository.
   * @return first index i in savedLog, where all log after i is valid part of new log
   * -1 if not enough commits in firstBlock
   */
  private static int getFirstSafeIndex(@NotNull List<TimeCommitParents> savedLog,
                                       @NotNull List<? extends TimeCommitParents> firstBlock,
                                       @NotNull Collection<VcsRef> refs) {
    Set<Hash> allUnresolvedLinkedHashes = new HashSet<Hash>();
    for (VcsRef ref: refs) {
      allUnresolvedLinkedHashes.add(ref.getCommitHash());
    }
    for (CommitParents commit : firstBlock) {
      allUnresolvedLinkedHashes.addAll(commit.getParents());
    }
    for (CommitParents commit : firstBlock) {
      if (commit.getParents().size() != 0) {
        allUnresolvedLinkedHashes.remove(commit.getHash());
      }
    }
    return getFirstUnTrackedIndex(savedLog, allUnresolvedLinkedHashes);
  }

  private static int getFirstUnTrackedIndex(@NotNull List<TimeCommitParents> commits, @NotNull Set<Hash> searchHashes) {
    int lastIndex = 0;
    for (CommitParents commit : commits) {
      if (searchHashes.size() == 0) {
        return lastIndex;
      }
      searchHashes.remove(commit.getHash());
      lastIndex++;
    }
    if (searchHashes.size() == 0) {
      return lastIndex;
    } else {
      return -1;
    }
  }

  private static Set<TimeCommitParents> getAllNewCommits(@NotNull List<TimeCommitParents> unsafePartSavedLog,
                                                         @NotNull List<? extends TimeCommitParents> firstBlock) {
    Set<Hash> existedCommitHashes = new HashSet<Hash>();
    for (CommitParents commit : unsafePartSavedLog) {
      existedCommitHashes.add(commit.getHash());
    }
    Set<TimeCommitParents> allNewsCommits = ContainerUtil.newHashSet();
    for (TimeCommitParents newCommit : firstBlock) {
      if (!existedCommitHashes.contains(newCommit.getHash())) {
        allNewsCommits.add(newCommit);
      }
    }
    return allNewsCommits;
  }


  private static class NewCommitIntegrator {
    private final List<TimeCommitParents> list;
    private final Map<Hash, TimeCommitParents> newCommitsMap;

    private NewCommitIntegrator(@NotNull List<TimeCommitParents> list, @NotNull Set<TimeCommitParents> newCommits) {
      this.list = list;
      newCommitsMap = ContainerUtil.newHashMap();
      for (TimeCommitParents commit : newCommits) {
        newCommitsMap.put(commit.getHash(), commit);
      }
    }

    // return insert Index
    private int insertToList(@NotNull TimeCommitParents commit) {
      if (!newCommitsMap.containsKey(commit.getHash())) {
        throw new IllegalStateException("Commit was inserted, but insert call again. Commit hash: " + commit.getHash());
      }
      //insert all parents commits
      for (Hash parentHash : commit.getParents()) {
        TimeCommitParents parentCommit = newCommitsMap.get(parentHash);
        if (parentCommit != null) {
          insertToList(parentCommit);
        }
      }

      int insertIndex = getInsertIndex(commit.getParents());
      list.add(insertIndex, commit);
      newCommitsMap.remove(commit.getHash());
      return insertIndex;
    }

    private int getInsertIndex(@NotNull Collection<Hash> parentHashes) {
      if (parentHashes.size() == 0) {
        return 0;
      }
      for (int i = 0; i < list.size(); i++) {
        if (parentHashes.contains(list.get(i).getHash())) {
          return i;
        }
      }
      throw new IllegalStateException("Not found parent Hash in list.");
    }

    private void insertAllCommits() {
      Iterator<TimeCommitParents> iterator = newCommitsMap.values().iterator();
      while (iterator.hasNext()) {
        insertToList(iterator.next());
        iterator = newCommitsMap.values().iterator();
      }
    }

    private List<TimeCommitParents> getResultList() {
      insertAllCommits();
      return list;
    }
  }


}
