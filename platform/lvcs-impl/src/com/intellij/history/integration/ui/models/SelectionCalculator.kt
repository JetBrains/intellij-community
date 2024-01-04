// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.history.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.history.core.Content;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SelectionCalculator {
  private static final Block EMPTY_BLOCK = new Block("", 0, 0);

  private final IdeaGateway myGateway;
  private final List<? extends Revision> myRevisions;
  private final int myFromLine;
  private final int myToLine;
  private final Int2ObjectMap<Block> myCache = new Int2ObjectOpenHashMap<>();

  public SelectionCalculator(IdeaGateway gw, List<? extends Revision> rr, int fromLine, int toLine) {
    myGateway = gw;
    myRevisions = rr;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  public boolean canCalculateFor(Revision r, Progress p) {
    try {
      doGetSelectionFor(r, p);
    }
    catch (ContentIsUnavailableException e) {
      return false;
    }
    return true;
  }

  public Block getSelectionFor(Revision r, Progress p) {
    return doGetSelectionFor(r, p);
  }

  private Block doGetSelectionFor(Revision r, Progress p) {
    int target = myRevisions.indexOf(r);
    return getSelectionFor(target, target + 1, p);
  }

  private Block getSelectionFor(int revisionIndex, int totalRevisions, Progress p) {
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

  private @Nullable String getRevisionContent(@NotNull Revision r) {
    Entry e = r.findEntry();
    if (e == null) return null;
    Content c = e.getContent();
    if (!c.isAvailable()) throw new ContentIsUnavailableException();
    return c.getString(e, myGateway);
  }

  private static final class ContentIsUnavailableException extends RuntimeException {
  }
}
