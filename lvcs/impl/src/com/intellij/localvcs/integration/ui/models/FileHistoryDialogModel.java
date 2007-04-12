package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.openapi.vfs.VirtualFile;

public class FileHistoryDialogModel extends HistoryDialogModel {
  public FileHistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    super(f, vcs, gw);
  }

  public boolean canShowDifference() {
    if (getLeftEntry().hasUnavailableContent()) return false;
    if (getRightEntry().hasUnavailableContent()) return false;
    return true;
  }

  public FileDifferenceModel getDifferenceModel() {
    return new FileDifferenceModel(getLeftEntry(), getRightEntry());
  }
}
