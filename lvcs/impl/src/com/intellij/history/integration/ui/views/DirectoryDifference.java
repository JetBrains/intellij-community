package com.intellij.history.integration.ui.views;

import com.intellij.history.integration.ui.models.DirectoryDifferenceModel;
import com.intellij.history.integration.ui.models.FileDifferenceModel;
import com.intellij.openapi.vcs.changes.Change;

public class DirectoryDifference extends Change {
  private DirectoryDifferenceModel myModel;

  public DirectoryDifference(DirectoryDifferenceModel m) {
    super(m.getContentRevision(0), m.getContentRevision(1));
    myModel = m;
  }

  public DirectoryDifferenceModel getModel() {
    return myModel;
  }

  public FileDifferenceModel getFileDifferenceModel() {
    return myModel.getFileDifferenceModel();
  }

  public boolean canShowFileDifference() {
    return myModel.canShowFileDifference();
  }
}
