/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dyoma
 */
public class DefaultExecutionResult implements ExecutionResult {
  private final ExecutionConsole myConsole;
  private final ProcessHandler myProcessHandler;
  private AnAction[] myActions;
  private AnAction[] myRestartActions;
  private final List<AnAction> myStopActions = new ArrayList<AnAction>();

  public DefaultExecutionResult() {
    myConsole = null;
    myProcessHandler = null;
    myActions = AnAction.EMPTY_ARRAY;
  }

  public DefaultExecutionResult(final ExecutionConsole console, @NotNull final ProcessHandler processHandler) {
    this(console, processHandler, AnAction.EMPTY_ARRAY);
  }

  public DefaultExecutionResult(final ExecutionConsole console, @NotNull final ProcessHandler processHandler, final AnAction... actions) {
    myConsole = console;
    myProcessHandler = processHandler;
    myActions = actions;
  }

  @Override
  public ExecutionConsole getExecutionConsole() {
    return myConsole;
  }

  @Override
  public AnAction[] getActions() {
    return myActions;
  }

  public void setActions(@NotNull final AnAction... actions) {
    myActions = actions;
  }

  public AnAction[] getRestartActions() {
    return myRestartActions;
  }

  public void setRestartActions(AnAction... restartActions) {
    myRestartActions = restartActions;
  }

  public void addStopAction(AnAction action) {
    myStopActions.add(action);
  }

  @NotNull
  public AnAction[] getAdditionalStopActions() {
    return myStopActions.toArray(new AnAction[myStopActions.size()]);
  }

  @Override
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  /**
   * @deprecated use {@link com.intellij.execution.actions.StopProcessAction}.
   * Will be removed in IDEA 14
   */
  public static class StopAction extends AnAction implements DumbAware {
    private final ProcessHandler myProcessHandler;

    public StopAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.stop.action.name"), null, AllIcons.Actions.Suspend);
      getTemplatePresentation().setEnabled(false);
      myProcessHandler = processHandler;
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      if(myProcessHandler.detachIsDefault()) {
        myProcessHandler.detachProcess();
      }
      else {
        myProcessHandler.destroyProcess();
      }
    }

    @Override
    public void update(final AnActionEvent event) {
      event.getPresentation().setEnabled(!myProcessHandler.isProcessTerminating() && !myProcessHandler.isProcessTerminated());
    }
  }
}
