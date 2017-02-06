/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.toolwindow;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
import com.intellij.debugger.memory.component.MemoryViewManager;
import com.intellij.debugger.memory.ui.ClassesFilteredView;
import com.intellij.execution.Executor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.RunContentWithExecutorListener;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.EventDispatcher;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.EventListener;

public class MemoryViewToolWindowFactory implements ToolWindowFactory, DumbAware {
  private static final Logger LOG = Logger.getInstance(ClassesFilteredView.class);
  public final static String TOOL_WINDOW_ID = "Memory View";

  private final JComponent myEmptyContent;
  private final JComponent myMemoryViewNotSupportedContent;

  private final EventDispatcher<ToolWindowStateListener> myDispatcher = EventDispatcher.create(ToolWindowStateListener.class);

  private volatile boolean myIsToolWindowVisible;

  {
    myEmptyContent = new JBLabel("Run debugging to see loaded classes", SwingConstants.CENTER);
    myMemoryViewNotSupportedContent = new JBLabel("The Memory View not available for this session",
                                                  SwingConstants.CENTER);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final MessageBusConnection connection = project.getMessageBus().connect(project);
    connection.subscribe(XDebuggerManager.TOPIC, new MyDebuggerStatusChangedListener());
    connection.subscribe(RunContentManager.TOPIC, new RunContentWithExecutorListener() {
      @Override
      public void contentSelected(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        ApplicationManager.getApplication().invokeLater(() -> updateCurrentMemoryView(project, toolWindow));
      }

      @Override
      public void contentRemoved(@Nullable RunContentDescriptor descriptor, @NotNull Executor executor) {
        ApplicationManager.getApplication().invokeLater(() -> updateCurrentMemoryView(project, toolWindow));
      }
    });

    ((ToolWindowImpl)toolWindow).getToolWindowManager()
      .addToolWindowManagerListener(new ToolWindowManagerAdapter() {
        @Override
        public void stateChanged() {
          final boolean isVisible = toolWindow.isVisible();

          if (isVisible != myIsToolWindowVisible) {
            myDispatcher.getMulticaster().visibilityChanged(isVisible);
            myIsToolWindowVisible = isVisible;
          }
        }
      }, project);

    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MemoryView.SettingsPopupActionGroup");

    ((ToolWindowEx)toolWindow).setAdditionalGearActions(group);
    toolWindow.getComponent().setLayout(new BorderLayout());

    for (XDebugSession session : XDebuggerManager.getInstance(project).getDebugSessions()) {
      registerMemoryView(session);
    }

    updateCurrentMemoryView(project, toolWindow);
  }

  private void registerMemoryView(@NotNull XDebugSession session) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    final XDebugProcess debugProcess = session.getDebugProcess();
    final Project project = session.getProject();
    final DebugProcess javaProcess =
      DebuggerManager.getInstance(session.getProject()).getDebugProcess(debugProcess.getProcessHandler());
    if (javaProcess instanceof DebugProcessImpl) {
      final DebugProcessImpl processImpl = (DebugProcessImpl)javaProcess;
      final ClassesFilteredView classesFilteredView = new ClassesFilteredView(debugProcess.getSession());
      myDispatcher.addListener(visible -> classesFilteredView.setActive(visible), classesFilteredView);

      final MemoryViewDebugProcessData data = new MemoryViewDebugProcessData(classesFilteredView);
      final ToolWindow toolWindow = getToolWindow(processImpl.getProject());

      classesFilteredView.setActive(toolWindow != null && toolWindow.isVisible());
      processImpl.putUserData(MemoryViewDebugProcessData.KEY, data);

      if (toolWindow != null) {
        updateCurrentMemoryView(project, toolWindow);
      }
    }
  }

  private void updateCurrentMemoryView(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    JComponent component = myEmptyContent;
    if (!project.isDisposed()) {
      final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();

      if (session != null) {
        final DebugProcess debugProcess = DebuggerManager.getInstance(project)
          .getDebugProcess(session.getDebugProcess().getProcessHandler());
        final MemoryViewDebugProcessData data = debugProcess != null ? debugProcess.getUserData(MemoryViewDebugProcessData.KEY) : null;

        component = data != null ? data.getClassesFilteredView() : myMemoryViewNotSupportedContent;
      }
    }

    replaceToolWindowContent(toolWindow, component);
  }

  private static void replaceToolWindowContent(@NotNull ToolWindow toolWindow, JComponent comp) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    final JComponent toolWindowComp = toolWindow.getComponent();
    toolWindowComp.removeAll();
    toolWindowComp.add(comp);
    toolWindowComp.repaint();
  }

  @Nullable
  private static ToolWindow getToolWindow(@NotNull Project project) {
    return MemoryViewManager.getInstance().getToolWindow(project);
  }

  public static class Condition implements com.intellij.openapi.util.Condition<Project> {
    @Override
    public boolean value(Project project) {
      final MessageBusConnection connection = project.getMessageBus().connect(project);
      connection.subscribe(XDebuggerManager.TOPIC, new XDebuggerManagerListener() {
        @Override
        public void processStarted(@NotNull XDebugProcess debugProcess) {
          updateIcon(debugProcess.getSession().getProject(), true);
        }

        @Override
        public void processStopped(@NotNull XDebugProcess debugProcess) {
          final Project project = debugProcess.getSession().getProject();
          final boolean enabled = Arrays.stream(XDebuggerManager.getInstance(project)
                                                  .getDebugSessions()).anyMatch(session -> !session.getDebugProcess().equals(debugProcess));
          updateIcon(project, enabled);
        }

        private void updateIcon(@NotNull Project project, boolean enabled) {
          final ToolWindow toolWindow = MemoryViewManager.getInstance().getToolWindow(project);
          if (toolWindow != null) {
            final Icon icon = enabled ? AllIcons.Debugger.MemoryView.ToolWindowEnabled : AllIcons.Debugger.MemoryView.ToolWindowDisabled;
            ApplicationManager.getApplication().invokeLater(() -> toolWindow.setIcon(icon));
          }
        }
      });
      return true;
    }
  }

  private final class MyDebuggerStatusChangedListener implements XDebuggerManagerListener {
    @Override
    public void processStarted(@NotNull XDebugProcess xDebugProcess) {
      ApplicationManager.getApplication().invokeLater(() -> registerMemoryView(xDebugProcess.getSession()));
    }

    @Override
    public void processStopped(@NotNull XDebugProcess xDebugProcess) {
      final XDebugSession session = xDebugProcess.getSession();
      if (xDebugProcess instanceof JavaDebugProcess) {
        final DebugProcessImpl process = ((JavaDebugProcess)xDebugProcess).getDebuggerSession().getProcess();

        final MemoryViewDebugProcessData data = process.getUserData(MemoryViewDebugProcessData.KEY);
        if (data != null) {
          data.getTrackedStacks().release();
          Disposer.dispose(data.getClassesFilteredView());
        }
      }

      ApplicationManager.getApplication().invokeLater(() -> updateView(session));
    }

    private void updateView(@NotNull XDebugSession debugSession) {
      final Project project = debugSession.getProject();
      if (!project.isDisposed()) {
        final ToolWindow toolWindow = getToolWindow(project);
        if (toolWindow != null) {
          updateCurrentMemoryView(project, toolWindow);
        }
      }
    }
  }

  private interface ToolWindowStateListener extends EventListener {
    void visibilityChanged(boolean visible);
  }
}
