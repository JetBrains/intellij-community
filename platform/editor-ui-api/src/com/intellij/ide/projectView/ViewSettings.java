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
package com.intellij.ide.projectView;

import com.intellij.ide.util.treeView.NodeOptions;

/**
 * The view settings for the project view.
 */
public interface ViewSettings extends NodeOptions {

  /**
   * The default view settings for the project view.
   */
  ViewSettings DEFAULT = new ViewSettings() {
    @Override
    public boolean isShowMembers() {
      return false;
    }

    @Override
    public boolean isAbbreviatePackageNames() {
      return false;
    }

    @Override
    public boolean isFlattenPackages() {
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

    @Override
    public boolean isStructureView() {
      return false;
    }

    @Override
    public boolean isShowModules() {
      return true;
    }
  };

  /**
   * Checks if the project view displays members of classes.
   *
   * @return true if class members are displayed, false otherwise.
   */
  boolean isShowMembers();

  /**
   * Checks if the project view displays the structure view pane.
   *
   * @return true if the structure view is displayed, false otherwise.
   */
  boolean isStructureView();

  /**
   * Checks if modules are shown on the "Packages" tab of the project view.
   *
   * @return true if the modules are shown, false otherwise.
   */
  boolean isShowModules();

  /**
   * @return {@code true} if modules should be shown in a flat list without grouping accordingly to qualified names
   */
  default boolean isFlattenModules() {
    return false;
  }

  /**
   * Checks if the project view displays URL for projects, modules and libraries.
   *
   * @return {@code true} if URL is displayed, {@code false} otherwise.
   */
  default boolean isShowURL() {
    return true;
  }
}
