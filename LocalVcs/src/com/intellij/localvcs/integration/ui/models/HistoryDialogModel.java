package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.Entry;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.localvcs.Label;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public abstract class HistoryDialogModel {
  protected ILocalVcs myVcs;
  protected VirtualFile myFile;
  private int myRightLabel;
  private int myLeftLabel;
  private List<Label> myLabelsCache;

  public HistoryDialogModel(VirtualFile f, ILocalVcs vcs) {
    myVcs = vcs;
    myFile = f;
  }

  private void initLabelsCache() {
    myLabelsCache = new ArrayList<Label>();
    addNotSavedVersionTo(myLabelsCache);
    myLabelsCache.addAll(myVcs.getLabelsFor(myFile.getPath()));
  }

  public List<Label> getLabels() {
    if (myLabelsCache == null) initLabelsCache();
    return myLabelsCache;
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
      myLeftLabel = first;
    }
    else {
      myRightLabel = first;
      myLeftLabel = second;
    }
  }
}
