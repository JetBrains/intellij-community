package com.intellij.usages.impl;

import com.intellij.usages.UsageTarget;

import javax.swing.tree.DefaultTreeModel;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2004
 * Time: 1:08:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageTargetNode extends Node {
  private UsageTarget myTarget;

  public UsageTargetNode(UsageTarget target, DefaultTreeModel model) {
    super(model);
    myTarget = target;
    setUserObject(target);
  }

  public String tree2string(int indent, String lineSeparator) {
    return myTarget.getName();
  }

  protected boolean isDataValid() {
    return myTarget.isValid();
  }

  protected boolean isDataReadOnly() {
    return myTarget.isReadOnly();
  }

  protected boolean isDataExcluded() {
    return false;
  }

  public UsageTarget getTarget() {
    return myTarget;
  }
}
