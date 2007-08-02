package com.intellij.history.integration.ui.views;

import com.intellij.history.integration.ui.models.DirectoryChangeModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.openapi.vcs.changes.Change;

public class DirectoryChange extends Change {
  private DirectoryChangeModel myModel;

  public DirectoryChange(DirectoryChangeModel m) {
    super(m.getContentRevision(0), m.getContentRevision(1));
    myModel = m;
  }

  public DirectoryChangeModel getModel() {
    return myModel;
  }

  public FileDifferenceModel getFileDifferenceModel() {
    return myModel.getFileDifferenceModel();
  }

  public boolean canShowFileDifference() {
    return myModel.canShowFileDifference();
  }
}
