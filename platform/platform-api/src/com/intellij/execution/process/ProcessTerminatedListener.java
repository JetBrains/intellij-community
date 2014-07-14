/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * @author dyoma
 */
public class ProcessTerminatedListener extends ProcessAdapter {
  @NonNls protected static final String EXIT_CODE_ENTRY = "$EXIT_CODE$";
  @NonNls protected static final String EXIT_CODE_REGEX = "\\$EXIT_CODE\\$";

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
    String message = IdeBundle.message("finished.with.exit.code.text.message", EXIT_CODE_ENTRY);
    attach(processHandler, project, "\n" + message + "\n");
  }

  public static void attach(final ProcessHandler processHandler) {
    attach(processHandler, null);
  }

  public void processTerminated(ProcessEvent event) {
    final ProcessHandler processHandler = event.getProcessHandler();
    processHandler.removeProcessListener(this);
    final String message = myProcessFinishedMessage.replaceAll(EXIT_CODE_REGEX, stringifyExitCode(event.getExitCode()));
    processHandler.notifyTextAvailable(message, ProcessOutputTypes.SYSTEM);
    if (myProject != null) ApplicationManager.getApplication().invokeLater(new Runnable(){
      public void run() {
        if (myProject.isDisposed()) return;
        StatusBar.Info.set(message, myProject);
      }
    });
  }

  @NotNull
  private static String stringifyExitCode(int exitCode) {
    // Quote from http://support.microsoft.com/kb/308558:
    //   If the result code has the "C0000XXX" format, the task did not complete successfully (the "C" indicates an error condition).
    //   The most common "C" error code is "0xC000013A: The application terminated as a result of a CTRL+C".
    if (SystemInfo.isWindows && exitCode >= 0xC0000000 && exitCode < 0xD0000000) {
      StringBuilder result = new StringBuilder();
      result.append(exitCode);
      result.append(" (0x").append(Integer.toHexString(exitCode).toUpperCase(Locale.ENGLISH));
      if (exitCode == 0xC000013A) {
        // reporting a detailed reason for a well-known exit code
        result.append(": interrupted by Ctrl+C");
      }
      result.append(")");
      return result.toString();
    }
    return String.valueOf(exitCode);
  }
}
