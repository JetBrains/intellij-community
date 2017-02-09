/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.memory.component;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.memory.ui.ClassesFilteredView;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class MemoryViewComponent extends AbstractProjectComponent {
  public MemoryViewComponent(@NotNull Project project) {
    super(project);
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new MyDebuggerStatusChangedListener());
  }

  private static final class MyDebuggerStatusChangedListener implements XDebuggerManagerListener {
    @Override
    public void processStarted(@NotNull XDebugProcess debugProcess) {
      final XDebugSession session = debugProcess.getSession();
      final DebugProcess javaProcess =
        DebuggerManager.getInstance(session.getProject()).getDebugProcess(debugProcess.getProcessHandler());
      if (javaProcess instanceof DebugProcessImpl) {
        final DebugProcessImpl processImpl = (DebugProcessImpl)javaProcess;
        ApplicationManager.getApplication().invokeLater(() -> {
          final RunnerLayoutUi ui = session.getUI();
          final ClassesFilteredView classesFilteredView = new ClassesFilteredView(session);
          classesFilteredView.setActive(true);
          final Content content =
            ui.createContent("MemoryView", classesFilteredView, "Memory View",
                             AllIcons.Debugger.MemoryView.ToolWindowEnabled, null);
          content.setCloseable(false);
          content.setPinned(true);
          content.setShouldDisposeContent(true);

          final MemoryViewDebugProcessData data = new MemoryViewDebugProcessData(classesFilteredView);
          processImpl.putUserData(MemoryViewDebugProcessData.KEY, data);
          session.getUI().addContent(content, -1, PlaceInGrid.right, true);
        });
      }
    }

    @Override
    public void processStopped(@NotNull XDebugProcess debugProcess) {
      ApplicationManager.getApplication().invokeLater(() ->{
        final Content memoryView = debugProcess.getSession().getUI().findContent("MemoryView");
        if (memoryView != null) {
          memoryView.setIcon(AllIcons.Debugger.MemoryView.ToolWindowDisabled);
        }
      });
    }
  }
}
