package com.intellij.localvcs;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalVcs {
  private File myDir;
  private List<StoredDirectoryRevision> myRevisions =
    new ArrayList<StoredDirectoryRevision>();

  public LocalVcs(File dir) {
    myDir = dir;
  }

  public WorkingDirectoryRevision getWorkingRevision() {
    return new WorkingDirectoryRevision(myDir);
  }

  public List<StoredDirectoryRevision> getRevisions() {
    return myRevisions;
  }

  public void commit() {
    myRevisions.add(getWorkingRevision().remember());
  }
}
