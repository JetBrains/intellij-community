// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("ComponentNotRegistered")
public class CloseAction extends AnAction implements DumbAware {
  private RunContentDescriptor myContentDescriptor;
  private final Project myProject;
  private Executor myExecutor;

  public CloseAction(Executor executor, RunContentDescriptor contentDescriptor, Project project) {
    myExecutor = executor;
    myContentDescriptor = contentDescriptor;
    myProject = project;
    ActionUtil.copyFrom(this, IdeActions.ACTION_CLOSE);
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setIcon(AllIcons.Actions.Cancel);
    templatePresentation.setText(ExecutionBundle.messagePointer("close.tab.action.name"));
    templatePresentation.setDescription(Presentation.NULL_STRING);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform();
  }

  public void perform() {
    final RunContentDescriptor contentDescriptor = getContentDescriptor();
    if (contentDescriptor == null) {
      return;
    }
    final boolean removedOk = RunContentManager.getInstance(myProject).removeRunContent(getExecutor(), contentDescriptor);
    if (removedOk) {
      myContentDescriptor = null;
      myExecutor = null;
    }
  }

  public RunContentDescriptor getContentDescriptor() {
    return myContentDescriptor;
  }

  public Executor getExecutor() {
    return myExecutor;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myContentDescriptor != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
