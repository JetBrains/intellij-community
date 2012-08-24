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

import com.intellij.icons.AllIcons;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 20
 * @author 2003
 */
public class IconSet {
  public static final Icon SOURCE_ROOT_FOLDER = AllIcons.Modules.SourceRoot;
  public static final Icon SOURCE_FOLDER = AllIcons.Modules.SourceFolder;

  public static final Icon TEST_ROOT_FOLDER = AllIcons.Modules.TestRoot;
  public static final Icon TEST_SOURCE_FOLDER = AllIcons.Modules.TestSourceFolder;

  public static final Icon EXCLUDE_FOLDER = AllIcons.Modules.ExcludeRoot;

  public static Icon getSourceRootIcon(boolean isTestSource) {
    return isTestSource ? TEST_ROOT_FOLDER : SOURCE_ROOT_FOLDER;
  }

  public static Icon getSourceFolderIcon(boolean isTestSource) {
    return isTestSource ? TEST_SOURCE_FOLDER : SOURCE_FOLDER;
  }

  public static Icon getExcludeIcon() {
    return EXCLUDE_FOLDER;
  }

}
