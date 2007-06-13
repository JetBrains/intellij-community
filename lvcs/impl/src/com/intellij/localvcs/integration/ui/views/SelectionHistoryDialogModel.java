package com.intellij.localvcs.integration.ui.views;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.ui.models.FileDifferenceModel;
import com.intellij.localvcs.integration.ui.models.FileHistoryDialogModel;
import com.intellij.localvcs.integration.ui.models.SelectionCalculator;
import com.intellij.localvcs.integration.ui.models.SelectionDifferenceModel;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class SelectionHistoryDialogModel extends FileHistoryDialogModel {
  private SelectionCalculator myCalculator;
  private int myFrom;
  private int myTo;

  public SelectionHistoryDialogModel(IdeaGateway gw, ILocalVcs vcs, VirtualFile f, int from, int to) {
    super(gw, vcs, f);
    myFrom = from;
    myTo = to;
  }

  @Override
  protected List<Revision> getRevisionsCache() {
    myCalculator = null;
    return super.getRevisionsCache();
  }

  @Override
  public boolean canShowDifference() {
    return getCalculator().canCalculateFor(getLeftRevision()) && getCalculator().canCalculateFor(getRightRevision());
  }

  @Override
  public FileDifferenceModel getDifferenceModel() {
    return new SelectionDifferenceModel(getCalculator(), getLeftRevision(), getRightRevision());
  }

  private SelectionCalculator getCalculator() {
    if (myCalculator == null) {
      myCalculator = new SelectionCalculator(getRevisions(), myFrom, myTo);
    }
    return myCalculator;
  }
}
