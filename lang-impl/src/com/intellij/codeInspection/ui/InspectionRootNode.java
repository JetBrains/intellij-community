package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author max
 */
public class InspectionRootNode extends InspectionTreeNode {
  private static final Icon INFO = IconLoader.getIcon("/general/ijLogo.png");
  private static final Icon APP_ICON = IconLoader.getIcon(ApplicationInfoEx.getInstanceEx().getSmallIconUrl()); 
  private final Project myProject;

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
    return isEmpty() ? INFO : APP_ICON;
  }
}
