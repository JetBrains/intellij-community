/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.unscramble;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.List;

/**
* Created by Irina.Chernushina on 7/13/13.
*/
public class ThreadDumpConsoleFactory implements AnalyzeStacktraceUtil.ConsoleFactory {
  private final Project myProject;
  private final List<ThreadState> myThreadDump;

  public ThreadDumpConsoleFactory(Project project, List<ThreadState> threadDump) {
    myProject = project;
    myThreadDump = threadDump;
  }

  public JComponent createConsoleComponent(ConsoleView consoleView, DefaultActionGroup toolbarActions) {
    return new ThreadDumpPanel(myProject, consoleView, toolbarActions, myThreadDump);
  }
}
