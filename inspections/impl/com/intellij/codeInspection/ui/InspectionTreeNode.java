package com.intellij.codeInspection.ui;

import com.intellij.openapi.vcs.FileStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Enumeration;

/**
 * @author max
 */
public abstract class InspectionTreeNode extends DefaultMutableTreeNode {
  protected InspectionTreeNode(Object userObject) {
    super(userObject);
  }

  @Nullable
  public abstract Icon getIcon(boolean expanded);

  public int getProblemCount() {
    int sum = 0;
    Enumeration enumeration = children();
    while (enumeration.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)enumeration.nextElement();
      sum += child.getProblemCount();
    }
    return sum;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isResolved(){
    return false;
  }

  public boolean appearsBold() {
    return false;
  }

  public FileStatus getNodeStatus(){
    return FileStatus.NOT_CHANGED;
  }
}
