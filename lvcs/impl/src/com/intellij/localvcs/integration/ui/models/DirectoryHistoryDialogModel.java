package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Difference;
import com.intellij.localvcs.integration.IdeaGateway;
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

  public boolean revert(DirectoryDifferenceModel m) {
    return Reverter.revert(myGateway, getLeftRevision(), m.getEntry(0), m.getEntry(1));
  }
}
