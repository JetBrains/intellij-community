package com.intellij.debugger.impl;

import com.intellij.openapi.project.Project;

/**
 * User: lex
 * Date: Nov 18, 2003
 * Time: 2:20:18 PM
 */
public abstract class HotSwapProgress {
  private final Project myProject;
  private Runnable myCancelWorker;
  private boolean myIsCancelled;

  public HotSwapProgress(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public abstract void addMessage(DebuggerSession session, final int type, final String text);

  public abstract void setText(String text);

  public abstract void setTitle(String title);

  public abstract void setFraction(double v);

  public void setCancelWorker(Runnable cancel) {
    myCancelWorker = cancel;
  }

  public void cancel() {
    myIsCancelled = true;
    if(myCancelWorker != null) {
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
