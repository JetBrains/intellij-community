package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;

import java.io.OutputStream;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class RemoteDebugProcessHandler extends ProcessHandler{
  private final Project myProject;

  public RemoteDebugProcessHandler(Project project) {
    myProject = project;
  }

  public void startNotify() {
    final DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    final DebugProcessAdapter listener = new DebugProcessAdapter() {
      //executed in manager thread
      public void processDetached(DebugProcess process, boolean closedByUser) {
        debugProcess.removeDebugProcessListener(this);
        notifyProcessDetached();
      }
    };
    debugProcess.addDebugProcessListener(listener);
    try {
      super.startNotify();
    }
    finally {
      // in case we added our listener too late, we may have lost processDetached notification,
      // so check here if process is detached
      if (debugProcess.isDetached()) {
        debugProcess.removeDebugProcessListener(listener);
        notifyProcessDetached();
      }
    }
  }

  protected void destroyProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(true);
    }
  }

  protected void detachProcessImpl() {
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(false);
    }
  }

  public boolean detachIsDefault() {
    return true;
  }

  public OutputStream getProcessInput() {
    return null;
  }
}
