// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;

import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteDebugProcessHandler extends ProcessHandler{
  private final Project myProject;
  private final boolean myAutoRestart;
  private final AtomicBoolean myClosedByUser = new AtomicBoolean();

  public RemoteDebugProcessHandler(Project project) {
    this(project, false);
  }

  public RemoteDebugProcessHandler(Project project, boolean autoRestart) {
    myProject = project;
    myAutoRestart = autoRestart;
  }

  @Override
  public void startNotify() {
    final DebugProcessListener listener = new DebugProcessAdapterImpl() {
      //executed in manager thread
      @Override
      public void processDetached(DebugProcessImpl process, boolean closedByUser) {
        if (!myAutoRestart || closedByUser || myClosedByUser.get()) {
          process.removeDebugProcessListener(this);
          notifyProcessDetached();
        }
        else {
          process.reattach(process.getSession().getDebugEnvironment());
        }
      }
    };
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
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

  @Override
  protected void destroyProcessImpl() {
    myClosedByUser.set(true);
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(true);
    }
  }

  @Override
  protected void detachProcessImpl() {
    myClosedByUser.set(true);
    DebugProcess debugProcess = DebuggerManager.getInstance(myProject).getDebugProcess(this);
    if(debugProcess != null) {
      debugProcess.stop(false);
    }
  }

  @Override
  public boolean detachIsDefault() {
    return true;
  }

  @Override
  public OutputStream getProcessInput() {
    return null;
  }
}
