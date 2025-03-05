// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessListener;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
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
