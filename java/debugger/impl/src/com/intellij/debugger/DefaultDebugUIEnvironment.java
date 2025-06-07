// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DefaultDebugUIEnvironment implements DebugUIEnvironment {
  private final ExecutionEnvironment myExecutionEnvironment;
  private final DebugEnvironment myModelEnvironment;

  public DefaultDebugUIEnvironment(@NotNull ExecutionEnvironment environment,
                                   RunProfileState state,
                                   RemoteConnection remoteConnection,
                                   boolean pollConnection) {
    myExecutionEnvironment = environment;
    myModelEnvironment = new DefaultDebugEnvironment(environment, state, remoteConnection, pollConnection);
  }

  @Override
  public DebugEnvironment getEnvironment() {
    return myModelEnvironment;
  }

  @Override
  public @Nullable RunContentDescriptor getReuseContent() {
    return myExecutionEnvironment.getContentToReuse();
  }

  @Override
  public Icon getIcon() {
    return getRunProfile().getIcon();
  }

  @Override
  public void initActions(RunContentDescriptor content, DefaultActionGroup actionGroup) {
    Executor executor = myExecutionEnvironment.getExecutor();
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RERUN), Constraints.FIRST);

    actionGroup.add(new CloseAction(executor, content, myExecutionEnvironment.getProject()));
    actionGroup.add(new ContextHelpAction(executor.getHelpId()));
  }

  @Override
  public @NotNull RunProfile getRunProfile() {
    return myExecutionEnvironment.getRunProfile();
  }
}
