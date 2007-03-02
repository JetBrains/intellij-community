package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.Difference;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.vfs.VirtualFile;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(VirtualFile f, ILocalVcs vcs) {
    super(f, vcs);
  }

  public String getTitle() {
    return myFile.getPath();
  }

  public DirectoryDifferenceModel getRootDifferenceNodeModel() {
    Difference d = getLeftLabel().getDifferenceWith(getRightLabel());
    return new DirectoryDifferenceModel(d);
  }
}
