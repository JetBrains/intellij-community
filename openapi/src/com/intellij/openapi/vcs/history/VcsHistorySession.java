package com.intellij.openapi.vcs.history;

import java.util.List;

public class VcsHistorySession {
  private final VcsRevisionNumber myRevisionNumber;
  private final List<VcsFileRevision> myRevisions;

  public VcsHistorySession(List<VcsFileRevision> revisions, VcsRevisionNumber revisionNumber) {
    myRevisionNumber = revisionNumber;
    myRevisions = revisions;
  }

  public List<VcsFileRevision> getRevisionList() {
    return myRevisions;
  }


  public VcsRevisionNumber getCurrentRevisionNumber() {
    return myRevisionNumber;
  }
}
