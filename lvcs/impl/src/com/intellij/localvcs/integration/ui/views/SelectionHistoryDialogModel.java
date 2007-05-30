package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.FileDifferenceModel;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.localvcs.integration.ui.models.SelectionCalculator;
import com.intellij.localvcs.integration.ui.models.SelectionDifferenceModel;
import com.intellij.openapi.vfs.VirtualFile;

public class SelectionHistoryDialogModel extends FileHistoryDialogModel {
  private SelectionCalculator myCalculator;

  public SelectionHistoryDialogModel(IdeaGateway gw, ILocalVcs vcs, VirtualFile f, int from, int to) {
    super(gw, vcs, f);
    myCalculator = new SelectionCalculator(getRevisions(), from, to);
  }

  @Override
  public boolean canShowDifference() {
    return myCalculator.canCalculateFor(getLeftRevision()) && myCalculator.canCalculateFor(getRightRevision());
  }

  @Override
  public FileDifferenceModel getDifferenceModel() {
    return new SelectionDifferenceModel(myCalculator, getLeftRevision(), getRightRevision());
  }
}
