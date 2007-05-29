package com.intellij.localvcs.integration.ui.models;

import com.intellij.diff.Block;
import com.intellij.diff.FindBlock;
import com.intellij.localvcs.core.revisions.Revision;

import java.util.List;

public class SelectedBlockCalculator {
  private List<Revision> myRevisions;
  private int myFromLine;
  private int myToLine;

  public SelectedBlockCalculator(List<Revision> rr, int fromLine, int toLine) {
    myRevisions = rr;
    myFromLine = fromLine;
    myToLine = toLine;
  }

  public Block getBlock(Revision r) {
    // todo test conversion and line-end
    Revision current = myRevisions.get(0);
    Block b = new Block(getRevisionContent(current), myFromLine, myToLine);

    if (r == current) return b;

    for (int i = 1; i < myRevisions.size(); i++) {
      current = myRevisions.get(i);
      b = new FindBlock(getRevisionContent(current), b).getBlockInThePrevVersion();
      if (current == r) break;
    }

    return b;
  }

  private String getRevisionContent(Revision r) {
    // todo test unavailable content
    return new String(r.getEntry().getContent().getBytes());
  }
}
