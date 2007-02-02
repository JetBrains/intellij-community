package com.intellij.localvcs.integration.ui;

import com.intellij.localvcs.Content;
import com.intellij.localvcs.Difference;
import com.intellij.localvcs.Entry;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vcs.checkin.DiffTreeNode;
import com.intellij.openapi.vcs.checkin.DifferenceType;
import com.intellij.openapi.vcs.ui.impl.PresentableDiffTreeNode;
import com.intellij.util.Icons;
import com.intellij.util.enumeration.EmptyEnumeration;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import java.util.Enumeration;

public class DifferenceNode implements TreeNode, PresentableDiffTreeNode {
  private DifferenceNode myParent;
  private Difference myDiff;

  public DifferenceNode(Difference d) {
    this(null, d);
  }

  private DifferenceNode(DifferenceNode parent, Difference d) {
    myParent = parent;
    myDiff = d;
  }

  public TreeNode getParent() {
    return myParent;
  }

  public DiffTreeNode getNodeParent() {
    return myParent;
  }

  public int getChildCount() {
    return myDiff.getChildren().size();
  }

  public TreeNode getChildAt(int i) {
    return new DifferenceNode(this, myDiff.getChildren().get(i));
  }

  public Enumeration children() {
    return EmptyEnumeration.INSTANCE;
  }

  public int getIndex(TreeNode n) {
    return myDiff.getChildren().indexOf(((DifferenceNode)n).myDiff);
  }

  public boolean isFile() {
    return myDiff.isFile();
  }

  public boolean isLeaf() {
    return isFile();
  }

  public boolean getAllowsChildren() {
    return false;
  }

  public String getPresentableText(int i) {
    Entry e = i == 0 ? myDiff.getLeft() : myDiff.getRight();
    return e == null ? "" : e.getName();
  }

  public DifferenceType getDifference() {
    return myDiff.getDifferenceType();
  }

  public void include() {
  }

  public void exclude() {
  }

  public boolean isExcluded() {
    return false;
  }

  public Icon getClosedIcon(int i) {
    return getFileIconOr(Icons.DIRECTORY_CLOSED_ICON);
  }

  public Icon getOpenIcon(int i) {
    return getFileIconOr(Icons.DIRECTORY_OPEN_ICON);
  }

  private Icon getFileIconOr(Icon dirIcon) {
    if (!isFile()) return dirIcon;
    return getFileTypeManager().getFileTypeByFileName(getEntryName()).getIcon();
  }

  private String getEntryName() {
    Entry e = myDiff.getLeft() != null ? myDiff.getLeft() : myDiff.getRight();
    return e.getName();
  }

  protected FileTypeManager getFileTypeManager() {
    return FileTypeManager.getInstance();
  }

  public Content getLeftContent() {
    return myDiff.getLeft().getContent();
  }

  public Content getRightContent() {
    return myDiff.getRight().getContent();
  }
}
