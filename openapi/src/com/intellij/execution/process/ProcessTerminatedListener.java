/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.process;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.util.Key;

/**
 * @author dyoma
 */
public class ProcessTerminatedListener extends ProcessAdapter {
  private static final Key<ProcessTerminatedListener> KEY = new Key<ProcessTerminatedListener>("processTerminatedListener");
  private final String myProcessFinishedMessage;
  private final Project myProject;

  private ProcessTerminatedListener(final Project project, final String processFinishedMessage) {
    myProject = project;
    myProcessFinishedMessage = processFinishedMessage;
  }

  public static void attach(final ProcessHandler processHandler, Project project, final String message) {
    final ProcessTerminatedListener previousListener = processHandler.getUserData(KEY);
    if (previousListener != null) {
      processHandler.removeProcessListener(previousListener);
      if (project == null) project = previousListener.myProject;
    }

    final ProcessTerminatedListener listener = new ProcessTerminatedListener(project, message);
    processHandler.addProcessListener(listener);
    processHandler.putUserData(KEY, listener);
  }

  public static void attach(final ProcessHandler processHandler, final Project project) {
    attach(processHandler, project, "\nProcess finished with exit code $EXIT_CODE$\n");
  }

  public static void attach(final ProcessHandler processHandler) {
    attach(processHandler, null);
  }

  public void processTerminated(ProcessEvent event) {
    final ProcessHandler processHandler = event.getProcessHandler();
    processHandler.removeProcessListener(this);
    final String message = myProcessFinishedMessage.replaceAll("\\$EXIT_CODE\\$", String.valueOf(event.getExitCode()));
    if (myProcessFinishedMessage != null)
      processHandler.notifyTextAvailable(message, ProcessOutputTypes.SYSTEM);
    if (myProject != null) ApplicationManager.getApplication().invokeLater(new Runnable(){
      public void run() {
        if (myProject.isDisposed()) return;
        WindowManager.getInstance().getStatusBar(myProject).setInfo(message);
      }
    });
  }
}
