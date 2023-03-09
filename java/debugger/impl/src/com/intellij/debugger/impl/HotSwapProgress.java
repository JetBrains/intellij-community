// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class HotSwapProgress {
  private final Project myProject;
  private Runnable myCancelWorker;
  private volatile boolean myIsCancelled;

  public HotSwapProgress(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public abstract void addMessage(DebuggerSession session, final int type, final String text);

  public abstract void setText(String text);

  public abstract void setTitle(@NotNull String title);

  public abstract void setFraction(double v);

  public void setCancelWorker(Runnable cancel) {
    myCancelWorker = cancel;
  }

  public void cancel() {
    myIsCancelled = true;
    if (myCancelWorker != null) {
      myCancelWorker.run();
    }
  }

  public void finished() {
  }

  public abstract void setDebuggerSession(DebuggerSession debuggerSession);

  public boolean isCancelled() {
    return myIsCancelled;
  }
}
