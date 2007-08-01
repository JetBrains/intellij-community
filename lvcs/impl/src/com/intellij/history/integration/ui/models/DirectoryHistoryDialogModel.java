package com.intellij.history.integration.ui.models;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.DirectoryReverter;
import com.intellij.history.integration.revertion.RevisionReverter;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(IdeaGateway gw, ILocalVcs vcs, VirtualFile f) {
    super(gw, vcs, f);
  }

  public String getTitle() {
    return myFile.getPath();
  }

  @Override
  protected RevisionReverter createRevisionReverter() {
    return createRevisionReverter(getLeftEntry(), getRightEntry());
  }

  public RevisionReverter createRevisionReverter(DirectoryDifferenceModel m) {
    return createRevisionReverter(m.getEntry(0), m.getEntry(1));
  }

  private RevisionReverter createRevisionReverter(Entry leftEntry, Entry rightEntry) {
    return new DirectoryReverter(myVcs, myGateway, getLeftRevision(), leftEntry, rightEntry);
  }
}
