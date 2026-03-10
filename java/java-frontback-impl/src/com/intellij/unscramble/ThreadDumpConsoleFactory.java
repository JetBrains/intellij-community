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
import com.intellij.threadDumpParser.ThreadState;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.JComponent;
import java.util.Collections;
import java.util.List;

public class ThreadDumpConsoleFactory implements AnalyzeStacktraceUtil.ConsoleFactory {
  private final Project myProject;
  private final ThreadDumpState myThreadDump;

  public ThreadDumpConsoleFactory(Project project, List<ThreadState> threadDump) {
    this(project, new ThreadDumpState(threadDump, Collections.emptyList()));
  }

  @ApiStatus.Internal
  public ThreadDumpConsoleFactory(Project project, ThreadDumpState threadDump) {
    myProject = project;
    myThreadDump = threadDump;
  }

  @Override
  public JComponent createConsoleComponent(ConsoleView consoleView, DefaultActionGroup toolbarActions) {
    return ThreadDumpPanel.createFromDumpItems(myProject, consoleView, toolbarActions, IntelliJThreadDumpParserKt.dumpItems(myThreadDump));
  }
}
