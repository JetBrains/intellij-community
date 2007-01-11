package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Difference;
import com.intellij.localvcs.LocalVcs;
import com.intellij.localvcs.ILocalVcs;
import com.intellij.openapi.vfs.VirtualFile;

public class DirectoryHistoryDialogModel extends HistoryDialogModel {
  public DirectoryHistoryDialogModel(VirtualFile f, ILocalVcs vcs) {
    super(f, vcs);
  }

  public Difference getDifference() {
    return getLeftLabel().getDifferenceWith(getRightLabel());
  }
}
