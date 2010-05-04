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
package com.intellij.execution;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.ArrayList;

/**
 * @author dyoma
 */
public class DefaultExecutionResult implements ExecutionResult {
  private final ExecutionConsole myConsole;
  private final ProcessHandler myProcessHandler;
  private AnAction[] myActions;
  private AnAction[] myRestartActions;
  private List<AnAction> myStopActions = new ArrayList<AnAction>();

  public DefaultExecutionResult(final ExecutionConsole console, final ProcessHandler processHandler) {
    this(console, processHandler, AnAction.EMPTY_ARRAY);
  }

  public DefaultExecutionResult(final ExecutionConsole console, final ProcessHandler processHandler, final AnAction... actions) {
    myConsole = console;
    myProcessHandler = processHandler;
    myActions = actions;
  }

  public ExecutionConsole getExecutionConsole() {
    return myConsole;
  }

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

  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public static class StopAction extends AnAction implements DumbAware {
    private final ProcessHandler myProcessHandler;

    public StopAction(final ProcessHandler processHandler) {
      super(ExecutionBundle.message("run.configuration.stop.action.name"), null, IconLoader.getIcon("/actions/suspend.png"));
      getTemplatePresentation().setEnabled(false);
      myProcessHandler = processHandler;
    }

    public void actionPerformed(final AnActionEvent e) {
      if(myProcessHandler.detachIsDefault()) {
        myProcessHandler.detachProcess();
      }
      else {
        myProcessHandler.destroyProcess();
      }
    }

    public void update(final AnActionEvent event) {
      event.getPresentation().setEnabled(!myProcessHandler.isProcessTerminating() && !myProcessHandler.isProcessTerminated());
    }
  }
}
