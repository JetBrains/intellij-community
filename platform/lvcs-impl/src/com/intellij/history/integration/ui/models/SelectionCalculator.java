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

package com.intellij.history.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.history.core.Content;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectionCalculator {
  private static final Block EMPTY_BLOCK = new Block("", 0, 0);
  
  private final IdeaGateway myGateway;
  private final List<Revision> myRevisions;
  private final int myFromLine;
  private final int myToLine;
  private final Map<Integer, Block> myCache = new HashMap<>();

  public SelectionCalculator(IdeaGateway gw, List<Revision> rr, int fromLine, int toLine) {
    myGateway = gw;
    myRevisions = rr;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  public boolean canCalculateFor(Revision r, Progress p) throws FilesTooBigForDiffException {
    try {
      doGetSelectionFor(r, p);
    }
    catch (ContentIsUnavailableException e) {
      return false;
    }
    return true;
  }

  public Block getSelectionFor(Revision r, Progress p) throws FilesTooBigForDiffException {
    return doGetSelectionFor(r, p);
  }

  private Block doGetSelectionFor(Revision r, Progress p) throws FilesTooBigForDiffException {
    int target = myRevisions.indexOf(r);
    return getSelectionFor(target, target + 1, p);
  }

  private Block getSelectionFor(int revisionIndex, int totalRevisions, Progress p) throws FilesTooBigForDiffException {
    Block cached = myCache.get(revisionIndex);
    if (cached != null) return cached;

    String content = getRevisionContent(myRevisions.get(revisionIndex));
    p.processed(((totalRevisions - revisionIndex) * 100) / totalRevisions);

    Block result;
    if (content == null) {
      result = EMPTY_BLOCK; 
    } else  if (revisionIndex == 0) {
      result = new Block(content, myFromLine, myToLine + 1);
    }
    else {
      Block prev = EMPTY_BLOCK;
      int i = revisionIndex;
      while(prev == EMPTY_BLOCK && i > 0) {
        i--;
        prev = getSelectionFor(i, totalRevisions, p);
      }
      result = prev.createPreviousBlock(content);
    }

    myCache.put(revisionIndex, result);

    return result;
  }

  @Nullable
  private String getRevisionContent(Revision r) {
    Entry e = r.findEntry();
    if (e == null) return null;
    Content c = e.getContent();
    if (!c.isAvailable()) throw new ContentIsUnavailableException();
    return c.getString(e, myGateway);
  }

  private static class ContentIsUnavailableException extends RuntimeException {
  }
}
