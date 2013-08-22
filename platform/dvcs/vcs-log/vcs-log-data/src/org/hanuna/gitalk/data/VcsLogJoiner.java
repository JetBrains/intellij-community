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
import com.intellij.vcs.log.Ref;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Kirill Likhodedov
 */
public class VcsLogJoiner {

  /**
   *
   *
   * @param savedLog       currently available part of the log.
   * @param firstBlock     the first n commits read from the VCS.
   * @param refs           all references (branches) of the repository.
   * @return New commits attached to the existing log structure.
   */
  @NotNull
  public List<? extends CommitParents> addCommits(@NotNull List<CommitParents> savedLog,
                                                  @NotNull List<? extends CommitParents> firstBlock, @NotNull Collection<Ref> refs) {
    int unsafeBlockSize = getFirstSafeIndex(savedLog, firstBlock, refs);
    if (unsafeBlockSize == -1) { // firstBlock not enough
      //TODO
      throw new IllegalStateException();
    }

    List<CommitParents> unsafePartSavedLog = new ArrayList<CommitParents>(savedLog.subList(0, unsafeBlockSize));
    Set<CommitParents> allNewsCommits = getAllNewCommits(unsafePartSavedLog, firstBlock);
    unsafePartSavedLog = new NewCommitIntegrator(unsafePartSavedLog, allNewsCommits).getResultList();

    return ContainerUtil.concat(unsafePartSavedLog, savedLog.subList(unsafeBlockSize, savedLog.size()));
  }

  /**
   *
   * @param savedLog       currently available part of the log.
   * @param firstBlock     the first n commits read from the VCS.
   * @param refs           all references (branches) of the repository.
   * @return -1 if not enough commits in firstBlock
   */
  private static int getFirstSafeIndex(@NotNull List<CommitParents> savedLog,
                                       @NotNull List<? extends CommitParents> firstBlock,
                                       @NotNull Collection<Ref> refs) {
    Set<Hash> allUnresolvedLinkedHashes = new HashSet<Hash>();
    for (Ref ref: refs) {
      allUnresolvedLinkedHashes.add(ref.getCommitHash());
    }
    for (CommitParents commit : firstBlock) {
      allUnresolvedLinkedHashes.addAll(commit.getParents());
    }
    for (CommitParents commit : firstBlock) {
      allUnresolvedLinkedHashes.remove(commit.getHash());
    }
    return getFirstUnTrackedIndex(savedLog, allUnresolvedLinkedHashes);
  }

  private static int getFirstUnTrackedIndex(@NotNull List<CommitParents> commits, @NotNull Set<Hash> searchHashes) {
    int lastIndex = 0;
    for (CommitParents commit : commits) {
      if (searchHashes.size() == 0) {
        return lastIndex;
      }
      searchHashes.remove(commit.getHash());
      lastIndex++;
    }
    return -1;
  }

  private static Set<CommitParents> getAllNewCommits(@NotNull List<CommitParents> unsafePartSavedLog,
                                                     @NotNull List<? extends CommitParents> firstBlock) {
    Set<Hash> existedCommitHashes = new HashSet<Hash>();
    for (CommitParents commit : unsafePartSavedLog) {
      existedCommitHashes.add(commit.getHash());
    }
    Set<CommitParents> allNewsCommits = new HashSet<CommitParents>();
    for (CommitParents newCommit : firstBlock) {
      if (!existedCommitHashes.contains(newCommit.getHash())) {
        allNewsCommits.add(newCommit);
      }
    }
    return allNewsCommits;
  }


  private static class NewCommitIntegrator {
    private final List<CommitParents> list;
    private final Map<Hash, CommitParents> newCommitsMap;

    private NewCommitIntegrator(@NotNull List<CommitParents> list, @NotNull Set<CommitParents> newCommits) {
      this.list = list;
      newCommitsMap = new HashMap<Hash, CommitParents>();
      for (CommitParents commit : newCommits) {
        newCommitsMap.put(commit.getHash(), commit);
      }
    }

    // return insert Index
    private int insertToList(@NotNull CommitParents commit) {
      if (!newCommitsMap.containsKey(commit.getHash())) {
        throw new IllegalStateException("Commit was inserted, but insert call again. Commit hash: " + commit.getHash());
      }
      //insert all parents commits
      for (Hash parentHash : commit.getParents()) {
        CommitParents parentCommit = newCommitsMap.get(parentHash);
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
      Iterator<CommitParents> iterator = newCommitsMap.values().iterator();
      while (iterator.hasNext()) {
        insertToList(iterator.next());
        iterator = newCommitsMap.values().iterator();
      }
    }

    private List<CommitParents> getResultList() {
      insertAllCommits();
      return list;
    }
  }


}
