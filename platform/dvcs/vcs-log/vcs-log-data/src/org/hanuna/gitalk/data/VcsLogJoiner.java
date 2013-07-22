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

import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.CommitParents;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.Ref;
import com.intellij.vcs.log.VcsCommitDetails;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Kirill Likhodedov
 */
public class VcsLogJoiner {

  /**
   *
   * @param savedLog       currently available part of the log.
   * @param firstBlock     the first n commits read from the VCS.
   * @param refs           all references (branches) of the repository.
   * @param wholeLogGetter the function which will read the whole log from the file, if the part stored in {@code savedLog} is not enough.
   * @return New commits attached to the existing log structure.
   */
  @NotNull
  public List<? extends CommitParents> addCommits(@NotNull List<CommitParents> savedLog,
                                                  @NotNull List<? extends CommitParents> firstBlock, @NotNull Collection<Ref> refs,
                                                  @NotNull Computable<List<CommitParents>> wholeLogGetter) {
    int unsafeBlockSize = getFirstSafeIndex(savedLog, firstBlock, refs);
    if (unsafeBlockSize == -1) { // firstBlock not enough
      //TODO
    }

    // TODO
    return firstBlock;
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
    return getLastSearchIndex(savedLog, allUnresolvedLinkedHashes);
  }

  private static int getLastSearchIndex(@NotNull List<CommitParents> commits, @NotNull Set<Hash> searchHashes) {
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

}
