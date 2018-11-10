// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.treeView;

/**
 * Common view settings for trees like the project view and packages view tree.
 *
 * @author Eugene Zhuravlev
 * @see com.intellij.ide.projectView.ViewSettings
 */
public interface NodeOptions {
  /**
   * Gets the value of the "Flatten Packages" option.
   *
   * @return the value of the "Flatten Packages" option.
   */
  boolean isFlattenPackages();

  /**
   * Gets the value of the "Abbreviate Qualified Package Names" option.
   *
   * @return the value of the "Abbreviate Qualified Package Names" option.
   */
  boolean isAbbreviatePackageNames();

  /**
   * Gets the value of the "Compact Empty Middle Packages" option.
   *
   * @return the value of the "Compact Empty Middle Packages" option.
   */
  boolean isHideEmptyMiddlePackages();

  /**
   * @return {@code true} if directories in a tree should be merged if possible
   */
  default boolean isCompactDirectories() {
    return false;
  }

  /**
   * Gets the value of the "Show/Hide Library Contents" option.
   *
   * @return true if the library contents are shown, false otherwise.
   */
  boolean isShowLibraryContents();

  class Immutable implements NodeOptions {
    public static final NodeOptions DEFAULT = new Immutable(null);

    private final boolean myFlattenPackages;
    private final boolean myAbbreviatePackageNames;
    private final boolean myHideEmptyMiddlePackages;
    private final boolean myCompactDirectories;
    private final boolean myShowLibraryContents;

    public Immutable(NodeOptions options) {
      myFlattenPackages = options != null && options.isFlattenPackages();
      myAbbreviatePackageNames = options != null && options.isAbbreviatePackageNames();
      myHideEmptyMiddlePackages = options != null && options.isHideEmptyMiddlePackages();
      myCompactDirectories = options != null && options.isCompactDirectories();
      myShowLibraryContents = options != null && options.isShowLibraryContents();
    }

    @Override
    public boolean isFlattenPackages() {
      return myFlattenPackages;
    }

    @Override
    public boolean isAbbreviatePackageNames() {
      return myAbbreviatePackageNames;
    }

    @Override
    public boolean isHideEmptyMiddlePackages() {
      return myHideEmptyMiddlePackages;
    }

    @Override
    public boolean isCompactDirectories() {
      return myCompactDirectories;
    }

    @Override
    public boolean isShowLibraryContents() {
      return myShowLibraryContents;
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) return true;
      if (object == null || !object.getClass().equals(getClass())) return false;
      NodeOptions options = (NodeOptions)object;
      return options.isFlattenPackages() == isFlattenPackages() &&
             options.isAbbreviatePackageNames() == isAbbreviatePackageNames() &&
             options.isHideEmptyMiddlePackages() == isHideEmptyMiddlePackages() &&
             options.isCompactDirectories() == isCompactDirectories() &&
             options.isShowLibraryContents() == isShowLibraryContents();
    }

    @Override
    public int hashCode() {
      int result = getClass().hashCode();
      result = 31 * result + Boolean.hashCode(isFlattenPackages());
      result = 31 * result + Boolean.hashCode(isAbbreviatePackageNames());
      result = 31 * result + Boolean.hashCode(isHideEmptyMiddlePackages());
      result = 31 * result + Boolean.hashCode(isCompactDirectories());
      result = 31 * result + Boolean.hashCode(isShowLibraryContents());
      return result;
    }
  }
}
