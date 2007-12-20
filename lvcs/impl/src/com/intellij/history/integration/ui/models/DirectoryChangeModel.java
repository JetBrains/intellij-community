package com.intellij.history.integration.ui.models;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.history.integration.IdeaGateway;
import com.intellij.openapi.vcs.changes.ContentRevision;

public class DirectoryChangeModel {
  private Difference myDiff;
  private IdeaGateway myGateway;
  private boolean isRightContentEditable;

  public DirectoryChangeModel(Difference d, IdeaGateway gw, boolean editableRightContent) {
    myDiff = d;
    myGateway = gw;
    isRightContentEditable = editableRightContent;
  }

  public boolean isFile() {
    return myDiff.isFile();
  }

  public String getEntryName(int i) {
    Entry e = getEntry(i);
    return e == null ? "" : e.getName();
  }

  public Entry getEntry(int i) {
    return i == 0 ? myDiff.getLeft() : myDiff.getRight();
  }

  public FileDifferenceModel getFileDifferenceModel() {
    return new EntireFileDifferenceModel(myGateway, myDiff.getLeft(), myDiff.getRight(), isRightContentEditable);
  }

  public boolean canShowFileDifference() {
    if (!isFile()) return false;
    if (getEntry(0) == null || getEntry(1) == null) return false;
    if (getEntry(0).hasUnavailableContent()) return false;
    if (getEntry(1).hasUnavailableContent()) return false;
    return true;
  }

  public ContentRevision getContentRevision(int i) {
    return i == 0 ? myDiff.getLeftContentRevision() : myDiff.getRightContentRevision();
  }
}