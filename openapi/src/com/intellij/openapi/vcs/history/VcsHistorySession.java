package com.intellij.openapi.vcs.history;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import com.intellij.openapi.util.Comparing;

public abstract class VcsHistorySession {
  private final List<VcsFileRevision> myRevisions;
  private VcsRevisionNumber myCachedRevisionNumber;

  public VcsHistorySession(List<VcsFileRevision> revisions) {
    myRevisions = revisions;
    myCachedRevisionNumber = calcCurrentRevisionNumber();
  }

  public List<VcsFileRevision> getRevisionList() {
    return myRevisions;
  }

  /**
   * This method should return actual value for current revision (it can be changed after submit for example)
   * @return current file revision, null if file does not exist anymore
   */

  @Nullable
  protected abstract VcsRevisionNumber calcCurrentRevisionNumber();

  public synchronized final VcsRevisionNumber getCurrentRevisionNumber(){
    return myCachedRevisionNumber;
  }

  public synchronized boolean refresh() {
    final VcsRevisionNumber oldValue = myCachedRevisionNumber;
    myCachedRevisionNumber = calcCurrentRevisionNumber();
    return !Comparing.equal(oldValue, myCachedRevisionNumber);
  }
}
