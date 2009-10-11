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
import com.intellij.history.core.storage.Content;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// Optimization: we do not need to build revisions list, though we have to provide
// correct number of timestamps for each possible revision for comparator.
// Therefore we have to move along the changelist, revert only content changes
// and record file and changeset timestamps to call comparator with.
public class ByteContentRetriever extends ChangeSetsProcessor {
  private final FileRevisionTimestampComparator myComparator;

  private long myCurrentFileTimestamp;
  private Content myCurrentFileContent;

  public ByteContentRetriever(LocalVcs vcs, String path, FileRevisionTimestampComparator c) {
    super(vcs, path);
    myComparator = c;

    myCurrentFileContent = myEntry.getContent();
    myCurrentFileTimestamp = myEntry.getTimestamp();
  }

  public byte[] getResult() {
    try {
      process();
    }
    catch (ContentFoundException ignore) {
      return myCurrentFileContent.getBytesIfAvailable();
    }

    return null;
  }

  @Override
  protected List<Change> collectChanges() {
    try {
      final List<Change> result = new ArrayList<Change>();

      myVcs.acceptRead(new ChangeVisitor() {
        @Override
        public void begin(ChangeSet c) {
          if (c.affects(myEntry)) result.add(c);
        }
      });

      return result;
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  protected void nothingToVisit() {
    // visit current version
    doVisit();
  }

  @Override
  protected void visitLabel(Change c) {
  }

  @Override
  public void visitRegular(Change c) {
    doVisit();
    recordContentAndTimestamp(c);
  }

  @Override
  protected void visitFirstAvailableNonCreational(Change c) {
    doVisit();
  }

  void doVisit() {
    if (myComparator.isSuitable(myCurrentFileTimestamp)) {
      throw new ContentFoundException();
    }
  }

  private void recordContentAndTimestamp(Change c) {
    for (Change each : c.getChanges()) {
      if (!each.isFileContentChange()) continue;
      if (!each.affectsOnlyInside(myEntry)) continue;

      ContentChange cc = (ContentChange)each;

      myCurrentFileTimestamp = cc.getOldTimestamp();
      myCurrentFileContent = cc.getOldContent();
    }
  }


  private static class ContentFoundException extends RuntimeException {
  }
}
