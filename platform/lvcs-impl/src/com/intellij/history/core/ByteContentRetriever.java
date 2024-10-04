/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core;

import com.intellij.history.FileRevisionTimestampComparator;
import com.intellij.history.core.changes.Change;
import com.intellij.history.core.changes.ChangeSet;
import com.intellij.history.core.changes.ChangeVisitor;
import com.intellij.history.core.changes.ContentChange;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// Optimization: we do not need to build revisions list, though we have to provide
// correct number of timestamps for each possible revision for comparator.
// Therefore we have to move along the changelist, revert only content changes
// and record file and changeset timestamps to call comparator with.
@ApiStatus.Internal
public final class ByteContentRetriever {
  private final LocalHistoryFacade myVcs;
  private final FileRevisionTimestampComparator myComparator;
  private final @NotNull String myPath;

  private long myCurrentFileTimestamp;
  private Content myCurrentFileContent;

  public ByteContentRetriever(IdeaGateway gateway, LocalHistoryFacade vcs, VirtualFile file, FileRevisionTimestampComparator c) {
    myPath = file.getPath();
    myVcs = vcs;
    myComparator = c;

    Entry e = gateway.createTransientEntry(file);
    myCurrentFileContent = e.getContent();
    myCurrentFileTimestamp = e.getTimestamp();
  }

  public byte[] getResult() {
    // optimization: do not collect changes if current revision will do
    if (myComparator.isSuitable(myCurrentFileTimestamp)) {
      return myCurrentFileContent.getBytesIfAvailable();
    }

    List<ChangeSet> changes = collectChanges();

    if (changes.isEmpty() && myComparator.isSuitable(myCurrentFileTimestamp)) {
      // visit current version
      return myCurrentFileContent.getBytesIfAvailable();
    }

    for (ChangeSet c : changes) {
      if (myComparator.isSuitable(myCurrentFileTimestamp)) {
        return myCurrentFileContent.getBytesIfAvailable();
      }
      recordContentAndTimestamp(c);
    }

    return null;
  }

  private @NotNull List<ChangeSet> collectChanges() {
    List<ChangeSet> changes = new ArrayList<>();
    myVcs.accept(new ChangeVisitor() {
      @Override
      public void begin(ChangeSet c) {
        if (c.anyChangeMatches(change -> change.affectsPath(myPath))) changes.add(c);
      }
    });
    return changes;
  }

  private void recordContentAndTimestamp(ChangeSet c) {
    // todo what if the path is being changed during changes?
    for (Change each : c.getChanges()) {
      if (!(each instanceof ContentChange cc)) continue;
      if (!cc.affectsPath(myPath)) continue;

      myCurrentFileTimestamp = cc.getOldTimestamp();
      myCurrentFileContent = cc.getOldContent();
    }
  }
}
