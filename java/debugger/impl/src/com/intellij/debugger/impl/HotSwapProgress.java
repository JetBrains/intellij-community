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
  private volatile boolean myIsCancelled;

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
