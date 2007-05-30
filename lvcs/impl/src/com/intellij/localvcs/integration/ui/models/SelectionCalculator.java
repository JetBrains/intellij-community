package com.intellij.localvcs.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.diff.FindBlock;
import com.intellij.localvcs.core.revisions.Revision;

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

  public Block getSelectionFor(Revision r) {
    return getBlock(myRevisions.indexOf(r));
  }

  private Block getBlock(int revisionIndex) {
    Block cached = myCache.get(revisionIndex);
    if (cached != null) return cached;

    String content = getRevisionContent(myRevisions.get(revisionIndex));

    Block result;
    if (revisionIndex == 0) {
      result = new Block(content, myFromLine, myToLine);
    }
    else {
      Block prev = getBlock(revisionIndex - 1);
      result = new FindBlock(content, prev).getBlockInThePrevVersion();
    }

    myCache.put(revisionIndex, result);
    return result;
  }

  private String getRevisionContent(Revision r) {
    // todo test conversion and line-end
    // todo test unavailable content
    return new String(r.getEntry().getContent().getBytes());
  }
}
