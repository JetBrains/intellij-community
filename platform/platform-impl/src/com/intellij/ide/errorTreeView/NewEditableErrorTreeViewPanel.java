/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class NewEditableErrorTreeViewPanel extends NewErrorTreeViewPanel {


  public NewEditableErrorTreeViewPanel(Project project, String helpId) {
    this(project, helpId, true);
  }

  public NewEditableErrorTreeViewPanel(Project project, String helpId, boolean createExitAction) {
    this(project, helpId, createExitAction, true);
  }

  public NewEditableErrorTreeViewPanel(Project project, String helpId, boolean createExitAction, boolean createToolbar) {
    this(project, helpId, createExitAction, createToolbar, null);
  }

  public NewEditableErrorTreeViewPanel(Project project,
                                       String helpId,
                                       boolean createExitAction,
                                       boolean createToolbar,
                                       @Nullable Runnable rerunAction) {
    super(project, helpId, createExitAction, createToolbar, rerunAction);
    NewErrorTreeEditor.install(myTree);
  }
}
