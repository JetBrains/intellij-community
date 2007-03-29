package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.integration.IdeaGateway;
import com.intellij.localvcs.integration.Reverter;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public abstract class HistoryDialogModel {
  protected ILocalVcs myVcs;
  protected VirtualFile myFile;
  protected IdeaGateway myGateway;
  private int myRightLabel;
  private int myLeftLabel;
  private List<Label> myLabelsCache;

  public HistoryDialogModel(VirtualFile f, ILocalVcs vcs, IdeaGateway gw) {
    myVcs = vcs;
    myFile = f;
    myGateway = gw;
  }

  public List<Label> getLabels() {
    if (myLabelsCache == null) initLabelsCache();
    return myLabelsCache;
  }

  private void initLabelsCache() {
    myLabelsCache = new ArrayList<Label>();
    addNotSavedVersionTo(myLabelsCache);
    myLabelsCache.addAll(myVcs.getLabelsFor(myFile.getPath()));
  }

  protected void addNotSavedVersionTo(List<Label> l) {
  }

  protected Label getLeftLabel() {
    return getLabels().get(myLeftLabel);
  }

  protected Label getRightLabel() {
    return getLabels().get(myRightLabel);
  }

  protected Entry getLeftEntry() {
    return getLeftLabel().getEntry();
  }

  protected Entry getRightEntry() {
    return getRightLabel().getEntry();
  }

  public void selectLabels(int first, int second) {
    if (first == second) {
      myRightLabel = 0;
      myLeftLabel = first == -1 ? 0 : first;
    }
    else {
      myRightLabel = first;
      myLeftLabel = second;
    }
  }

  public boolean revert() {
    return Reverter.revert(myGateway, getLeftLabel(), getLeftEntry(), getRightEntry());
  }

  public boolean canRevert() {
    return myLeftLabel > 0 && myRightLabel == 0;
  }
}
