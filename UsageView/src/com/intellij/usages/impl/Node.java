package com.intellij.usages.impl;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:38:12 PM
 * To change this template use File | Settings | File Templates.
 */
abstract class Node extends DefaultMutableTreeNode {
  private boolean myIsValid = true;
  protected final DefaultTreeModel myTreeModel;
  private Boolean myIsReadOnly = null;
  private boolean myExcluded = false;

  protected Node(DefaultTreeModel model) {
    myTreeModel = model;
  }

  /**
   * debug method for producing string tree presentation
   * @param indent
   * @param lineSeparator
   */
  public abstract String tree2string(int indent, String lineSeparator);

  void appendSpaces(StringBuffer s, int spaces) {
    for (int i = 0; i < spaces; i++) s.append(' ');
  }

  protected abstract boolean isDataValid();
  protected abstract boolean isDataReadOnly();
  protected abstract boolean isDataExcluded();

  public final boolean isValid() {
    return myIsValid;
  }

  public final boolean isReadOnly() {
    if (myIsReadOnly == null) myIsReadOnly = Boolean.valueOf(isDataReadOnly());
    return myIsReadOnly.booleanValue();
  }

  public final boolean isExcluded() {
    return myExcluded;
  }

  public final void update() {
    boolean isDataValid = isDataValid();
    boolean isReadOnly = isDataReadOnly();
    boolean isExcluded = isDataExcluded();
    if (isDataValid != myIsValid || myIsReadOnly == null || isReadOnly != myIsReadOnly.booleanValue() || isExcluded != myExcluded) {
      myIsValid = isDataValid;
      myExcluded = isExcluded;
      myIsReadOnly = Boolean.valueOf(isReadOnly);
      myTreeModel.nodeChanged(this);
    }
  }
}
