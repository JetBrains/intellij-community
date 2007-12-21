package com.intellij.history.integration.ui.views;

import com.intellij.history.core.ILocalVcs;
import com.intellij.history.core.revisions.Revision;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.history.integration.LocalHistoryBundle;
import com.intellij.history.integration.revertion.ChangeReverter;
import com.intellij.history.integration.revertion.Reverter;
import com.intellij.history.integration.revertion.SelectionReverter;
import com.intellij.history.integration.ui.models.*;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.util.List;

public class SelectionHistoryDialogModel extends FileHistoryDialogModel {
  private SelectionCalculator myCalculatorCache;
  private int myFrom;
  private int myTo;

  public SelectionHistoryDialogModel(IdeaGateway gw, ILocalVcs vcs, VirtualFile f, int from, int to) {
    super(gw, vcs, f);
    myFrom = from;
    myTo = to;
  }

  @Override
  protected List<Revision> getRevisionsCache() {
    myCalculatorCache = null;
    return super.getRevisionsCache();
  }

  @Override
  public boolean canShowDifference(RevisionProcessingProgress p) {
    p.processingLeftRevision();
    if (!getCalculator().canCalculateFor(getLeftRevision(), p)) return false;

    p.processingRightRevision();
    return getCalculator().canCalculateFor(getRightRevision(), p);
  }

  @Override
  public FileDifferenceModel getDifferenceModel() {
    return new SelectionDifferenceModel(myGateway,
                                        getCalculator(),
                                        getLeftRevision(),
                                        getRightRevision(),
                                        myFrom,
                                        myTo,
                                        isCurrentRevisionSelected());
  }

  private SelectionCalculator getCalculator() {
    if (myCalculatorCache == null) {
      myCalculatorCache = new SelectionCalculator(getRevisions(), myFrom, myTo);
    }
    return myCalculatorCache;
  }

  @Override
  protected Reverter createRevisionReverter() {
    return new SelectionReverter(myVcs, myGateway, getCalculator(), getLeftRevision(), getRightEntry(), myFrom, myTo);
  }

  @Override
  protected ChangeReverter createChangeReverter() {
    return new ChangeReverter(myVcs, myGateway, getRightRevision().getCauseChange()) {
      @Override
      public List<String> askUserForProceeding() throws IOException {
        List<String> result = super.askUserForProceeding();
        result.add(LocalHistoryBundle.message("revert.message.will.revert.whole.file"));
        return result;
      }
    };
  }
}
