package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.Label;
import com.intellij.localvcs.Difference;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;
import java.util.ArrayList;

public class DirectoryHistoryDialogModel {
  private LocalVcs myVcs;
  private VirtualFile myFile;
  private int myRightLabel;
  private int myLeftLabel;

  public DirectoryHistoryDialogModel(VirtualFile f, LocalVcs vcs) {
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

  private List<Label> getVcsLabels() {
    List<Label> result = new ArrayList<Label>();
    result.addAll(myVcs.getLabelsFor(myFile.getPath()));

    return result;
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

  public Difference getDifference() {
    Label left = getVcsLabels().get(myLeftLabel);
    Label right = getVcsLabels().get(myRightLabel);

    return left.getDifferenceWith(right);
  }
}
