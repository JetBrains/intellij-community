/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.diff;

import com.intellij.openapi.project.Project;

import java.awt.*;

/**
 * @deprecated
 */
public class DiffPanelFactory {

  /**
   * @deprecated
   */
  public static DiffPanel createDiffPanel(Project project, boolean enableToolbar) {
    return createDiffPanel(project, null, enableToolbar);
  }

  /**
   * @deprecated 
   * @param ownerWindow this window will be disposed, when user clicks on the line number
   */
  public static DiffPanel createDiffPanel(Project project, Window ownerWindow, boolean enableToolbar) {
    return DiffManager.getInstance().createDiffPanel(ownerWindow, project);
  }
}
