package com.intellij.ide.util.treeView;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 7, 2003
 * Time: 1:19:41 PM
 */
public interface NodeOptions {
  boolean isFlattenPackages();
  boolean isAbbreviatePackageNames();
  boolean isHideEmptyMiddlePackages();
  boolean isShowLibraryContents();

  NodeOptions DEFAULT_OPTIONS = new NodeOptions() {
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
      return false;
    }
  };
}
