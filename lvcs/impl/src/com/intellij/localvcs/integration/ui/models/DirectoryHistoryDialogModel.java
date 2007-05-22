package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Difference;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.revert.DirectoryReverter;
import com.intellij.localvcs.integration.revert.RevisionReverter;
import com.intellij.openapi.vfs.VirtualFile;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    super(f, vcs, gw);
  }

  public String getTitle() {
    return myFile.getPath();
  }

  public DirectoryDifferenceModel getRootDifferenceNodeModel() {
    Difference d = getLeftRevision().getDifferenceWith(getRightRevision());
    return new DirectoryDifferenceModel(d);
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
