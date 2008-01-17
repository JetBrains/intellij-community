package com.intellij.ide.projectView.impl;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;

/**
 * @author ven
 * */

public abstract class ProjectTreeStructure extends AbstractProjectTreeStructure {
  private final String myId;

  public ProjectTreeStructure(Project project, final String ID) {
    super(project);
    myId = ID;
  }

  public boolean isFlattenPackages() {
    return ProjectView.getInstance(myProject).isFlattenPackages(myId);
  }

  public boolean isShowMembers() {
    return ProjectView.getInstance(myProject).isShowMembers(myId);
  }

  public boolean isHideEmptyMiddlePackages() {
    return ProjectView.getInstance(myProject).isHideEmptyMiddlePackages(myId);
  }

  public boolean isAbbreviatePackageNames() {
    return ProjectView.getInstance(myProject).isAbbreviatePackageNames(myId);
  }

  public boolean isShowLibraryContents() {
    return ProjectView.getInstance(myProject).isShowLibraryContents(myId);
  }

  public boolean isShowModules() {
    return ProjectView.getInstance(myProject).isShowModules(myId);
  }
}