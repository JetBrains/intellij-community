package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.ILocalVcs;
import com.intellij.localvcs.core.revisions.Revision;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.revert.ChangeReverter;
import com.intellij.localvcs.integration.revert.Reverter;
import com.intellij.localvcs.integration.revert.RevisionReverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public abstract class HistoryDialogModel {
  protected ILocalVcs myVcs;
  protected VirtualFile myFile;
  protected IdeaGateway myGateway;
  private int myRightRevisionIndex;
  private int myLeftRevisionIndex;
  private boolean myIsChangesSelected = false;
  private List<Revision> myRevisionsCache;


  public HistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myFile = f;
    myGateway = gw;
  }

  public List<Revision> getRevisions() {
    if (myRevisionsCache == null) initRevisionsCache();
    return myRevisionsCache;
  }

  private void initRevisionsCache() {
    myGateway.registerUnsavedDocuments(myVcs);
    myRevisionsCache = myVcs.getRevisionsFor(myFile.getPath());
  }

  protected Revision getLeftRevision() {
    return getRevisions().get(myLeftRevisionIndex);
  }

  protected Revision getRightRevision() {
    return getRevisions().get(myRightRevisionIndex);
  }

  protected Entry getLeftEntry() {
    return getLeftRevision().getEntry();
  }

  protected Entry getRightEntry() {
    return getRightRevision().getEntry();
  }

  public void selectRevisions(int first, int second) {
    doSelect(first, second);
    myIsChangesSelected = false;
  }

  public void selectChanges(int first, int second) {
    doSelect(first, second + 1);
    myIsChangesSelected = true;
  }

  private void doSelect(int first, int second) {
    if (first == second) {
      myRightRevisionIndex = 0;
      myLeftRevisionIndex = first == -1 ? 0 : first;
    }
    else {
      myRightRevisionIndex = first;
      myLeftRevisionIndex = second;
    }
  }

  protected boolean isCurrentRevisionSelected() {
    return myRightRevisionIndex == 0;
  }

  public Reverter createReverter() {
    if (myIsChangesSelected) return createChangeReverter();
    return createRevisionReverter();
  }

  private ChangeReverter createChangeReverter() {
    return new ChangeReverter(myVcs, myGateway, getRightRevision().getCauseChange());
  }

  protected abstract RevisionReverter createRevisionReverter();

  public boolean isRevertEnabled() {
    if (myIsChangesSelected) {
      return myLeftRevisionIndex - myRightRevisionIndex == 1;
    }
    return isCurrentRevisionSelected() && myLeftRevisionIndex > 0;
  }
}
