package com.intellij.codeInspection.ui;

import com.intellij.util.Icons;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionPackageNode extends InspectionTreeNode {

  public InspectionPackageNode(String packageName) {
    super(packageName);
  }

  public String getPackageName() {
    return (String) getUserObject();
  }

  public Icon getIcon(boolean expanded) {
    return expanded ? Icons.PACKAGE_OPEN_ICON : Icons.PACKAGE_ICON;
  }
}
