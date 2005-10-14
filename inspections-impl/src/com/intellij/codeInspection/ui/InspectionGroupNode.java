package com.intellij.codeInspection.ui;

import com.intellij.ide.IconUtilEx;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionGroupNode extends InspectionTreeNode {
  private static final Icon EMPTY = new EmptyIcon(0, IconUtilEx.getEmptyIcon(false).getIconHeight());

  public InspectionGroupNode(String groupTitle) {
    super(groupTitle);
  }

  public String getGroupTitle() {
    return (String) getUserObject();
  }

  public Icon getIcon(boolean expanded) {
    return EMPTY;
  }

  public boolean appearsBold() {
    return true;
  }
}
