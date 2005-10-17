package com.intellij.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.openapi.project.Project;

class TestProjectTreeStructure extends AbstractProjectTreeStructure {
  public TestProjectTreeStructure(Project project) {
    super(project);
  }

  public boolean isShowMembers() {
    return false;
  }

  public boolean isFlattenPackages() {
    return false;
  }

  public boolean isAbbreviatePackageNames() {
    return false;
  }

  public boolean isHideEmptyMiddlePackages() {
    return false;
  }

  public boolean isShowLibraryContents() {
    return true;
  }

  public boolean isShowModules() {
    return true;
  }
}
