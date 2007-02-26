package com.intellij.localvcs.integration.ui.models;

import com.intellij.localvcs.Difference;
import com.intellij.localvcs.Entry;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.util.Icons;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DifferenceNodeModel {
  private Difference myDiff;

  public DifferenceNodeModel(Difference d) {
    myDiff = d;
  }

  public List<DifferenceNodeModel> getChildren() {
    List<DifferenceNodeModel> result = new ArrayList<DifferenceNodeModel>();
    for (Difference d : myDiff.getChildren()) {
      result.add(new DifferenceNodeModel(d));
    }
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

  private Entry getEntry(int i) {
    return i == 0 ? myDiff.getLeft() : myDiff.getRight();
  }

  public FileDifferenceModel getFileDifferenceModel() {
    return new FileDifferenceModel(myDiff.getLeft(), myDiff.getRight());
  }
}