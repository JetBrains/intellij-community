// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;

public abstract class DebuggerManager {
  public static DebuggerManager getInstance(Project project) {
    return project.getService(DebuggerManager.class);
  }

  public abstract DebugProcess getDebugProcess(ProcessHandler processHandler);

  public abstract void addDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract void removeDebugProcessListener(ProcessHandler processHandler, DebugProcessListener listener);

  public abstract boolean isDebuggerManagerThread();

  public abstract void addClassNameMapper(NameMapper mapper);

  public abstract void removeClassNameMapper(NameMapper mapper);

  public abstract String getVMClassQualifiedName(PsiClass aClass);
}
