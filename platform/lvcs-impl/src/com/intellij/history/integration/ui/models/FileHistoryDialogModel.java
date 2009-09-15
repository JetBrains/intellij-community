package com.intellij.history.integration.ui.models;

import com.intellij.history.core.LocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.DifferenceReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(IdeaGateway gw, LocalVcs vcs, VirtualFile f) {
    super(gw, vcs, f);
  }

  public abstract FileDifferenceModel getDifferenceModel();

  @Override
  protected Reverter createRevisionReverter() {
    Revision l = getLeftRevision();
    Revision r = getRightRevision();
    return new DifferenceReverter(myVcs, myGateway, l.getDifferencesWith(r), l);
  }
}
