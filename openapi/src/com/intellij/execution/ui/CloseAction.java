/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;

public class CloseAction extends AnAction {
  private JavaProgramRunner myRunner;
  private RunContentDescriptor myContentDescriptor;
  private final Project myProject;

  public CloseAction(JavaProgramRunner runner, RunContentDescriptor contentDescriptor, Project project) {
    myRunner = runner;
    myContentDescriptor = contentDescriptor;
    myProject = project;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE_ACTIVE_TAB));
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setIcon(IconLoader.getIcon("/actions/cancel.png"));
    templatePresentation.setText("Close");
    templatePresentation.setDescription(null);
  }

  public void actionPerformed(AnActionEvent e) {
    if (myContentDescriptor == null) {
      return;
    }
    final boolean removedOk = ExecutionManager.getInstance(myProject).getContentManager().removeRunContent(myRunner, myContentDescriptor);
    if (removedOk) {
      myContentDescriptor = null;
      myRunner = null;
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(myContentDescriptor != null);
  }
}