package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.Icons;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionRootNode extends InspectionTreeNode {
  private static final Icon INFO = IconLoader.getIcon("/general/ijLogo.png");
  private Project myProject;

  public InspectionRootNode(Project project) {
    super(project);
    myProject = project;
  }

  public String toString() {
    return isEmpty() ? InspectionsBundle.message("inspection.empty.root.node.text") :
           myProject.getName();
  }

  private boolean isEmpty() {
    return getChildCount() == 0;
  }

  public Icon getIcon(boolean expanded) {
    return isEmpty() ? INFO : Icons.PROJECT_ICON;
  }
}
