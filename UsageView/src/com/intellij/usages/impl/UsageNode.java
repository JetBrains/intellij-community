package com.intellij.usages.impl;

import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;

import javax.swing.tree.DefaultTreeModel;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 16, 2004
 * Time: 5:40:43 PM
 * To change this template use File | Settings | File Templates.
 */
class UsageNode extends Node implements Comparable<UsageNode>, Navigatable {
  private final Usage myUsage;
  private boolean myUsageExcluded = false;

  public UsageNode(Usage usage, DefaultTreeModel model) {
    super(model);
    setUserObject(usage);
    myUsage = usage;
  }

  public String toString() {
    return myUsage.toString();
  }

  public String tree2string(int indent, String lineSeparator) {
    StringBuffer result = new StringBuffer();
    appendSpaces(result, indent);
    result.append(myUsage.toString());
    return result.toString();
  }

  public int compareTo(UsageNode usageNode) {
    return 0;
  }

  public Usage getUsage() {
    return myUsage;
  }

  public void navigate(boolean requestFocus) {
    myUsage.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myUsage.isValid() && myUsage.canNavigate();
  }

  protected boolean isDataValid() {
    return myUsage.isValid();
  }

  protected boolean isDataReadOnly() {
    return myUsage.isReadOnly();
  }

  protected boolean isDataExcluded() {
    return myUsageExcluded;
  }

  public void setUsageExcluded(boolean usageExcluded) {
    myUsageExcluded = usageExcluded;
  }
}
