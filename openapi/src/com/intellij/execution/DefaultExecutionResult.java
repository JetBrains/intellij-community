/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

/**
 * @author dyoma
 */
public class DefaultExecutionResult implements ExecutionResult {
  private final ExecutionConsole myConsole;
  private final ProcessHandler myProcessHandler;
  private AnAction[] myActions;

  public DefaultExecutionResult(final ExecutionConsole console, final ProcessHandler processHandler) {
    this(console, processHandler, AnAction.EMPTY_ARRAY);
  }

  public DefaultExecutionResult(final ExecutionConsole console, final ProcessHandler processHandler, final AnAction[] actions) {
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

  public void setActions(final AnAction[] actions) {
    myActions = actions != null ? actions : AnAction.EMPTY_ARRAY;
  }

  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public static class StopAction extends AnAction {
    private final ProcessHandler myProcessHandler;

    public StopAction(final ProcessHandler processHandler) {
      super("Stop", null, IconLoader.getIcon("/actions/suspend.png"));
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
