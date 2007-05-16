package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Difference;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.revert.DirectoryReverter;
import com.intellij.localvcs.integration.revert.Reverter;
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
  public Reverter createReverter() {
    return createReverter(getLeftEntry(), getRightEntry());
  }

  public Reverter createReverter(DirectoryDifferenceModel m) {
    return createReverter(m.getEntry(0), m.getEntry(1));
  }

  private Reverter createReverter(Entry leftEntry, Entry rightEntry) {
    return new DirectoryReverter(myVcs, myGateway, getLeftRevision(), leftEntry, rightEntry);
  }
}
