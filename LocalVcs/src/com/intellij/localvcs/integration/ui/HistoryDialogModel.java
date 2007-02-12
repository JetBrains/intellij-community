package com.intellij.localvcs.integration.ui;

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

  public HistoryDialogModel(VirtualFile f, ILocalVcs vcs) {
    myVcs = vcs;
    myFile = f;
  }

  public List<String> getLabels() {
    List<String> result = new ArrayList<String>();
    for (Label l : getVcsLabels()) {
      result.add(l.getName());
    }
    return result;
  }

  protected List<Label> getVcsLabels() {
    List<Label> result = new ArrayList<Label>();

    addCurrentVersionTo(result);
    result.addAll(myVcs.getLabelsFor(myFile.getPath()));

    return result;
  }

  protected void addCurrentVersionTo(List<Label> l) {
  }

  protected Label getLeftLabel() {
    return getVcsLabels().get(myLeftLabel);
  }

  protected Label getRightLabel() {
    return getVcsLabels().get(myRightLabel);
  }

  protected Entry getLeftEntry() {
    return getLeftLabel().getEntry();
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
