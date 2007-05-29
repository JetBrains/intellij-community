package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.core.revisions.Difference;
import com.intellij.localvcs.core.tree.Entry;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DirectoryDifferenceModel {
  private Difference myDiff;

  public DirectoryDifferenceModel(Difference d) {
    myDiff = d;
  }

  public List<DirectoryDifferenceModel> getChildren() {
    List<DirectoryDifferenceModel> result = new ArrayList<DirectoryDifferenceModel>();
    for (Difference d : myDiff.getChildren()) {
      result.add(new DirectoryDifferenceModel(d));
    }
    Collections.sort(result, new MyComparator());
    return result;
  }

  public boolean isFile() {
    return myDiff.isFile();
  }

  public String getEntryName(int i) {
    Entry e = getEntry(i);
    return e == null ? "" : e.getName();
  }

  public Difference.Kind getDifferenceKind() {
    return myDiff.getKind();
  }

  public Icon getClosedIcon(int i, FileTypeManager tm) {
    return getFileIconOr(i, Icons.DIRECTORY_CLOSED_ICON, tm);
  }

  public Icon getOpenIcon(int i, FileTypeManager tm) {
    return getFileIconOr(i, Icons.DIRECTORY_OPEN_ICON, tm);
  }

  private Icon getFileIconOr(int i, Icon dirIcon, FileTypeManager tm) {
    if (!isFile()) return dirIcon;
    if (getEntry(i) == null) return null;
    return tm.getFileTypeByFileName(getEntry(i).getName()).getIcon();
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

  private static class MyComparator implements Comparator<DirectoryDifferenceModel> {
    public int compare(DirectoryDifferenceModel l, DirectoryDifferenceModel r) {
      if (l.isFile() != r.isFile()) return l.isFile() ? 1 : -1;
      return l.getAnyEntryName().compareToIgnoreCase(r.getAnyEntryName());
    }
  }

  private String getAnyEntryName() {
    Entry e = getEntry(0);
    if (e == null) e = getEntry(1);
    return e.getName();
  }
}