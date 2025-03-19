// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.errorTreeView;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NewEditableErrorTreeViewPanel extends NewErrorTreeViewPanel {


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
