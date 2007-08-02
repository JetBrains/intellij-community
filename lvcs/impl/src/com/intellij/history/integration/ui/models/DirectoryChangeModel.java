package com.intellij.history.integration.ui.models;

import com.intellij.history.core.revisions.Difference;
import com.intellij.history.core.tree.Entry;
import com.intellij.openapi.vcs.changes.ContentRevision;

import java.util.ArrayList;
import java.util.List;

public class DirectoryChangeModel {
  private Difference myDiff;

  public DirectoryChangeModel(Difference d) {
    myDiff = d;
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
    return new EntireFileDifferenceModel(myDiff.getLeft(), myDiff.getRight());
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