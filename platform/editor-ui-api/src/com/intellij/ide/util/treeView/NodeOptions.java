/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
   * Gets the value of the "Show/Hide Library Contents" option.
   *
   * @return true if the library contents are shown, false otherwise.
   */
  boolean isShowLibraryContents();

  /**
   * The default tree view settings.
   */
  NodeOptions DEFAULT_OPTIONS = new NodeOptions() {
    @Override
    public boolean isFlattenPackages() {
      return false;
    }

    @Override
    public boolean isAbbreviatePackageNames() {
      return false;
    }

    @Override
    public boolean isHideEmptyMiddlePackages() {
      return false;
    }

    @Override
    public boolean isShowLibraryContents() {
      return false;
    }
  };
}
