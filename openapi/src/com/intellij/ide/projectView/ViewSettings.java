package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.NodeOptions;

public interface ViewSettings extends NodeOptions {

  static ViewSettings DEFAULT = new ViewSettings() {
    public boolean isShowMembers() {
      return false;
    }

    public boolean isAbbreviatePackageNames() {
      return false;
    }

    public boolean isFlattenPackages() {
      return false;
    }

    public boolean isHideEmptyMiddlePackages() {
      return false;
    }

    public boolean isShowLibraryContents() {
      return false;
    }

    public boolean isStructureView() {
      return false;
    }

    public boolean isShowModules() {
      return true;
    }
  };

  boolean isShowMembers();

  boolean isStructureView();

  boolean isShowModules();
}
