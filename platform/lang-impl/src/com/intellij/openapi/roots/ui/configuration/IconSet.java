/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 20
 * @author 2003
 */
public class IconSet {
  public static final Icon SOURCE_ROOT_FOLDER = IconLoader.getIcon("/modules/sourceRootClosed.png");
  public static final Icon SOURCE_ROOT_FOLDER_EXPANDED = IconLoader.getIcon("/modules/sourceRootOpened.png");
  public static final Icon SOURCE_FOLDER = IconLoader.getIcon("/modules/sourceClosed.png");
  public static final Icon SOURCE_FOLDER_EXPANDED = IconLoader.getIcon("/modules/sourceOpened.png");

  public static final Icon TEST_ROOT_FOLDER = IconLoader.getIcon("/modules/testRootClosed.png");
  public static final Icon TEST_ROOT_FOLDER_EXPANDED = IconLoader.getIcon("/modules/testRootOpened.png");
  public static final Icon TEST_SOURCE_FOLDER = IconLoader.getIcon("/modules/testSourceClosed.png");
  public static final Icon TEST_SOURCE_FOLDER_EXPANDED = IconLoader.getIcon("/modules/testSourceOpened.png");

  public static final Icon EXCLUDE_FOLDER = IconLoader.getIcon("/modules/excludeRootClosed.png");
  public static final Icon EXCLUDE_FOLDER_EXPANDED = IconLoader.getIcon("/modules/excludeRootOpened.png");

  public static Icon getSourceRootIcon(boolean isTestSource, final boolean isExpanded) {
    if (isExpanded) {
      return isTestSource ? TEST_ROOT_FOLDER_EXPANDED : SOURCE_ROOT_FOLDER_EXPANDED;
    }
    return isTestSource ? TEST_ROOT_FOLDER : SOURCE_ROOT_FOLDER;
  }

  public static Icon getSourceFolderIcon(boolean isTestSource, final boolean isExpanded) {
    if (isExpanded) {
      return isTestSource ? TEST_SOURCE_FOLDER_EXPANDED : SOURCE_FOLDER_EXPANDED;
    }
    return isTestSource ? TEST_SOURCE_FOLDER : SOURCE_FOLDER;
  }

  public static Icon getExcludeIcon(boolean isExpanded) {
    return isExpanded? EXCLUDE_FOLDER_EXPANDED : EXCLUDE_FOLDER;
  }

}
