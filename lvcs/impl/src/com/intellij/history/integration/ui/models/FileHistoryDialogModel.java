package com.intellij.history.integration.ui.models;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.revertion.FileReverter;
import com.intellij.history.integration.revertion.RevisionReverter;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(IdeaGateway gw, ILocalVcs vcs, VirtualFile f) {
    super(gw, vcs, f);
  }

  public abstract boolean canShowDifference(RevisionProcessingProgress p);

  public abstract FileDifferenceModel getDifferenceModel();

  @Override
  protected RevisionReverter createRevisionReverter() {
    return new FileReverter(myGateway, getLeftRevision(), getLeftEntry(), getRightEntry());
  }
}
