/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.tracking;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import org.jetbrains.annotations.NotNull;

public abstract class ClassPreparedListener {
  private final XDebugSession myDebugSession;
  private final Project myProject;

  protected ClassPreparedListener(@NotNull String className,
                                  @NotNull XDebugSession debugSession) {
    myProject = debugSession.getProject();
    myDebugSession = debugSession;
    DebugProcessImpl debugProcess = getDebugProcess();
    ClassPrepareRequestor request = new MyClassPreparedRequest();
    ClassPrepareRequest classPrepareRequest = debugProcess.getRequestsManager()
        .createClassPrepareRequest(request, className);
    if (classPrepareRequest != null) {
      classPrepareRequest.enable();
    }
  }

  public abstract void onClassPrepared(@NotNull ReferenceType referenceType);

  private DebugProcessImpl getDebugProcess() {
    return (DebugProcessImpl) DebuggerManager.getInstance(myProject)
        .getDebugProcess(myDebugSession.getDebugProcess().getProcessHandler());
  }

  private final class MyClassPreparedRequest implements ClassPrepareRequestor {
    @Override
    public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
      getDebugProcess().getRequestsManager().deleteRequest(this);
      onClassPrepared(referenceType);
    }
  }
}
