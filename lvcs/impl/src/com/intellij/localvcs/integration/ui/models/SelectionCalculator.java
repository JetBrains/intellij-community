package com.intellij.localvcs.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.diff.FindBlock;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.storage.Content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectionCalculator {
  private List<Revision> myRevisions;
  private int myFromLine;
  private int myToLine;
  private Map<Integer, Block> myCache = new HashMap<Integer, Block>();

  public SelectionCalculator(List<Revision> rr, int fromLine, int toLine) {
    myRevisions = rr;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  public boolean canCalculateFor(Revision r) {
    try {
      getSelectionFor(myRevisions.indexOf(r));
    }
    catch (ContentIsUnavailableException e) {
      return false;
    }
    return true;
  }

  public Block getSelectionFor(Revision r) {
    return getSelectionFor(myRevisions.indexOf(r));
  }

  private Block getSelectionFor(int revisionIndex) {
    Block cached = myCache.get(revisionIndex);
    if (cached != null) return cached;

    String content = getRevisionContent(myRevisions.get(revisionIndex));

    Block result;
    if (revisionIndex == 0) {
      result = new Block(content, myFromLine, myToLine);
    }
    else {
      Block prev = getSelectionFor(revisionIndex - 1);
      result = new FindBlock(content, prev).getBlockInThePrevVersion();
    }

    myCache.put(revisionIndex, result);
    return result;
  }

  private String getRevisionContent(Revision r) {
    Content c = r.getEntry().getContent();
    if (!c.isAvailable()) throw new ContentIsUnavailableException();
    return new String(c.getBytes());
  }

  private static class ContentIsUnavailableException extends RuntimeException {
  }
}
