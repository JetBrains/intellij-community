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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
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
  private static final String MEMORY_VIEW_CONTENT_ID = "MemoryView";

  public MemoryViewComponent(@NotNull Project project) {
    super(project);
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new MyDebuggerStatusChangedListener());
  }

  private static final class MyDebuggerStatusChangedListener implements XDebuggerManagerListener {
    @Override
    public void processStarted(@NotNull XDebugProcess debugProcess) {
      final XDebugSession session = debugProcess.getSession();
      final Project project = session.getProject();
      final DebugProcess javaProcess =
        DebuggerManager.getInstance(project).getDebugProcess(debugProcess.getProcessHandler());
      if (javaProcess instanceof DebugProcessImpl) {
        final DebugProcessImpl processImpl = (DebugProcessImpl)javaProcess;
        ApplicationManager.getApplication().invokeLater(() -> {
          if (project.isDisposed()) {
            return;
          }

          final InstancesTracker tracker = InstancesTracker.getInstance(project);
          final RunnerLayoutUi ui = session.getUI();
          final ClassesFilteredView classesFilteredView = new ClassesFilteredView(session, processImpl, tracker);
          classesFilteredView.setActive(true);
          final Content memoryViewContent =
            ui.createContent(MEMORY_VIEW_CONTENT_ID, classesFilteredView, "Memory View",
                             AllIcons.Debugger.MemoryView.Active, null);

          memoryViewContent.setCloseable(false);
          memoryViewContent.setPinned(true);
          memoryViewContent.setShouldDisposeContent(true);

          final MemoryViewDebugProcessData data = new MemoryViewDebugProcessData(classesFilteredView);
          processImpl.putUserData(MemoryViewDebugProcessData.KEY, data);
          ui.addContent(memoryViewContent, 0, PlaceInGrid.right, true);

          ui.addListener(new ContentManagerAdapter() {
            @Override
            public void selectionChanged(ContentManagerEvent event) {
              final Content content = event.getContent();
              if (content == memoryViewContent) {
                if (ContentManagerEvent.ContentOperation.add.equals(event.getOperation())) {
                  classesFilteredView.setActive(true);
                }
                else if (ContentManagerEvent.ContentOperation.remove.equals(event.getOperation())) {
                  classesFilteredView.setActive(false);
                }
              }
            }
          }, classesFilteredView);
        });
      }
    }

    @Override
    public void processStopped(@NotNull XDebugProcess debugProcess) {
      ApplicationManager.getApplication().invokeLater(() -> {
        final Content memoryView = debugProcess.getSession().getUI().findContent(MEMORY_VIEW_CONTENT_ID);
        if (memoryView != null) {
          memoryView.setIcon(AllIcons.Debugger.MemoryView.Inactive);
        }
      });
    }
  }
}
