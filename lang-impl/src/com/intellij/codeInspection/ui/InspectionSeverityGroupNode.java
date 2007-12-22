package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;

import javax.swing.*;


/**
 * User: anna
 * Date: Mar 15, 2005
 */
public class InspectionSeverityGroupNode extends InspectionTreeNode{

  private HighlightDisplayLevel myLevel;
  private final Project myProject;

  public InspectionSeverityGroupNode(final Project project, final HighlightDisplayLevel level) {
    super(level);
    myLevel = level;
    myProject = project;
  }

  public Icon getIcon(boolean expanded) {
    return myLevel.getIcon();
  }

  public boolean appearsBold() {
    return true;
  }

  public String toString() {
    return StringUtil.capitalize(myLevel.toString().toLowerCase());
  }

  public HighlightDisplayLevel getSeverityLevel() {
    return myLevel;
  }

  public Project getProject() {
    return myProject;
  }
}
