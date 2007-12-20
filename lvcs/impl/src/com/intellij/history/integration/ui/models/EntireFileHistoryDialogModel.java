package com.intellij.history.integration.ui.models;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

public class EntireFileHistoryDialogModel extends FileHistoryDialogModel {
  public EntireFileHistoryDialogModel(IdeaGateway gw, ILocalVcs vcs, VirtualFile f) {
    super(gw, vcs, f);
  }

  public boolean canShowDifference(RevisionProcessingProgress p) {
    p.processingLeftRevision();
    if (getLeftEntry().hasUnavailableContent()) return false;

    p.processingRightRevision();
    return !getRightEntry().hasUnavailableContent();
  }

  public FileDifferenceModel getDifferenceModel() {
    return new EntireFileDifferenceModel(myGateway, getLeftEntry(), getRightEntry(), isCurrentRevisionSelected());
  }
}