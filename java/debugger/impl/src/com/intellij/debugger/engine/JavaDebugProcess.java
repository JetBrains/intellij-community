/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.AlternativeSourceNotificationProvider;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.ThreadsPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.ExecutionConsoleEx;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.sun.jdi.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * @author egor
 */
public class JavaDebugProcess extends XDebugProcess {
  private final DebuggerSession myJavaSession;
  private final JavaDebuggerEditorsProvider myEditorsProvider;
  private final XBreakpointHandler<?>[] myBreakpointHandlers;
  private final NodeManagerImpl myNodeManager;

  public static JavaDebugProcess create(@NotNull final XDebugSession session, final DebuggerSession javaSession) {
    JavaDebugProcess res = new JavaDebugProcess(session, javaSession);
    javaSession.getProcess().setXDebugProcess(res);
    return res;
  }

  protected JavaDebugProcess(@NotNull final XDebugSession session, final DebuggerSession javaSession) {
    super(session);
    myJavaSession = javaSession;
    myEditorsProvider = new JavaDebuggerEditorsProvider();
    final DebugProcessImpl process = javaSession.getProcess();

    List<XBreakpointHandler> handlers = new ArrayList<XBreakpointHandler>();
    handlers.add(new JavaBreakpointHandler.JavaLineBreakpointHandler(process));
    handlers.add(new JavaBreakpointHandler.JavaExceptionBreakpointHandler(process));
    handlers.add(new JavaBreakpointHandler.JavaFieldBreakpointHandler(process));
    handlers.add(new JavaBreakpointHandler.JavaMethodBreakpointHandler(process));
    handlers.add(new JavaBreakpointHandler.JavaWildcardBreakpointHandler(process));

    for (JavaBreakpointHandlerFactory factory : Extensions.getExtensions(JavaBreakpointHandlerFactory.EP_NAME)) {
      handlers.add(factory.createHandler(process));
    }

    myBreakpointHandlers = handlers.toArray(new XBreakpointHandler[handlers.size()]);

    myJavaSession.getContextManager().addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(final DebuggerContextImpl newContext, int event) {
        if (event == DebuggerSession.EVENT_PAUSE
            || event == DebuggerSession.EVENT_CONTEXT
            || event == DebuggerSession.EVENT_REFRESH
               && myJavaSession.isPaused()) {
          if (getSession().getSuspendContext() != newContext.getSuspendContext()) {
            process.getManagerThread().schedule(new DebuggerContextCommandImpl(newContext) {
              @Override
              public void threadAction() {
                SuspendContextImpl context = newContext.getSuspendContext();
                if (context != null) {
                  context.initExecutionStacks(newContext.getThreadProxy());

                  List<Pair<Breakpoint, Event>> descriptors =
                    DebuggerUtilsEx.getEventDescriptors(context);
                  if (!descriptors.isEmpty()) {
                    Breakpoint breakpoint = descriptors.get(0).getFirst();
                    XBreakpoint xBreakpoint = breakpoint.getXBreakpoint();
                    if (xBreakpoint != null) {
                      ((XDebugSessionImpl)getSession()).breakpointReachedNoProcessing(xBreakpoint, context);
                      return;
                    }
                  }
                  getSession().positionReached(context);
                }
              }
            });
          }
        }
        else if (event == DebuggerSession.EVENT_ATTACHED) {
          getSession().rebuildViews(); // to refresh variables views message
        }
      }
    });

    myNodeManager = new NodeManagerImpl(session.getProject(), null) {
      @Override
      public DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext) {
        return new DebuggerTreeNodeImpl(null, descriptor);
      }

      @Override
      public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
        return new DebuggerTreeNodeImpl(null, descriptor);
      }

      @Override
      public DebuggerTreeNodeImpl createMessageNode(String message) {
        return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
      }
    };
    session.addSessionListener(new XDebugSessionAdapter() {
      @Override
      public void sessionPaused() {
        saveNodeHistory();
        showAlternativeNotification(session.getCurrentStackFrame());
      }

      @Override
      public void stackFrameChanged() {
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          showAlternativeNotification(frame);
          StackFrameProxyImpl frameProxy = ((JavaStackFrame)frame).getStackFrameProxy();
          DebuggerContextUtil.setStackFrame(javaSession.getContextManager(), frameProxy);
          saveNodeHistory(frameProxy);
        }
      }

      private void showAlternativeNotification(@Nullable XStackFrame frame) {
        if (frame != null) {
          XSourcePosition position = frame.getSourcePosition();
          if (position != null) {
            VirtualFile file = position.getFile();
            if (!AlternativeSourceNotificationProvider.fileProcessed(file)) {
              EditorNotifications.getInstance(session.getProject()).updateNotifications(file);
            }
          }
        }
      }
    });
  }

  public void saveNodeHistory() {
    saveNodeHistory(getDebuggerStateManager().getContext().getFrameProxy());
  }

  private void saveNodeHistory(final StackFrameProxyImpl frameProxy) {
    myJavaSession.getProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        myNodeManager.setHistoryByContext(frameProxy);
      }

      @Override
      public Priority getPriority() {
        return Priority.NORMAL;
      }
    });
  }

  private DebuggerStateManager getDebuggerStateManager() {
    return myJavaSession.getContextManager();
  }

  public DebuggerSession getDebuggerSession() {
    return myJavaSession;
  }

  @NotNull
  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @Override
  public void startStepOver() {
    myJavaSession.stepOver(false);
  }

  @Override
  public void startStepInto() {
    myJavaSession.stepInto(false, null);
  }

  @Override
  public void startForceStepInto() {
    myJavaSession.stepInto(true, null);
  }

  @Override
  public void startStepOut() {
    myJavaSession.stepOut();
  }

  @Override
  public void stop() {
    myJavaSession.dispose();
    myNodeManager.dispose();
  }

  @Override
  public void startPausing() {
    myJavaSession.pause();
  }

  @Override
  public void resume() {
    myJavaSession.resume();
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position) {
    myJavaSession.runToCursor(position, false);
  }

  @NotNull
  @Override
  public XBreakpointHandler<?>[] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @Override
  public boolean checkCanInitBreakpoints() {
    return false;
  }

  @Nullable
  @Override
  protected ProcessHandler doGetProcessHandler() {
    return myJavaSession.getProcess().getProcessHandler();
  }

  @NotNull
  @Override
  public ExecutionConsole createConsole() {
    ExecutionConsole console = myJavaSession.getProcess().getExecutionResult().getExecutionConsole();
    if (console != null) return console;
    return super.createConsole();
  }

  @NotNull
  @Override
  public XDebugTabLayouter createTabLayouter() {
    return new XDebugTabLayouter() {
      @Override
      public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
        final ThreadsPanel panel = new ThreadsPanel(myJavaSession.getProject(), getDebuggerStateManager());
        final Content threadsContent = ui.createContent(
          DebuggerContentInfo.THREADS_CONTENT, panel, XDebuggerBundle.message("debugger.session.tab.threads.title"),
          AllIcons.Debugger.Threads, null);
        Disposer.register(threadsContent, panel);
        threadsContent.setCloseable(false);
        ui.addContent(threadsContent, 0, PlaceInGrid.left, true);
        ui.addListener(new ContentManagerAdapter() {
          @Override
          public void selectionChanged(ContentManagerEvent event) {
            if (event.getContent() == threadsContent) {
              if (threadsContent.isSelected()) {
                panel.setUpdateEnabled(true);
                if (panel.isRefreshNeeded()) {
                  panel.rebuildIfVisible(DebuggerSession.EVENT_CONTEXT);
                }
              }
              else {
                panel.setUpdateEnabled(false);
              }
            }
          }
        }, threadsContent);
      }

      @NotNull
      @Override
      public Content registerConsoleContent(@NotNull RunnerLayoutUi ui, @NotNull ExecutionConsole console) {
        Content content = null;
        if (console instanceof ExecutionConsoleEx) {
          ((ExecutionConsoleEx)console).buildUi(ui);
          content = ui.findContent(DebuggerContentInfo.CONSOLE_CONTENT);
        }
        if (content == null) {
          content = super.registerConsoleContent(ui, console);
        }
        return content;
      }
    };
  }

  @Override
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar, @NotNull DefaultActionGroup topToolbar, @NotNull DefaultActionGroup settings) {
    Constraints beforeRunner = new Constraints(Anchor.BEFORE, "Runner.Layout");
    leftToolbar.add(Separator.getInstance(), beforeRunner);
    leftToolbar.add(ActionManager.getInstance().getAction(DebuggerActions.DUMP_THREADS), beforeRunner);
    leftToolbar.add(Separator.getInstance(), beforeRunner);

    Constraints beforeSort = new Constraints(Anchor.BEFORE, "XDebugger.ToggleSortValues");
    settings.addAction(new WatchLastMethodReturnValueAction(), beforeSort);
    settings.addAction(new AutoVarsSwitchAction(), beforeSort);
  }

  private static class AutoVarsSwitchAction extends ToggleAction {
    private volatile boolean myAutoModeEnabled;

    public AutoVarsSwitchAction() {
      super(DebuggerBundle.message("action.auto.variables.mode"), DebuggerBundle.message("action.auto.variables.mode.description"), null);
      myAutoModeEnabled = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myAutoModeEnabled;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean enabled) {
      myAutoModeEnabled = enabled;
      DebuggerSettings.getInstance().AUTO_VARIABLES_MODE = enabled;
      XDebuggerUtilImpl.rebuildAllSessionsViews(e.getProject());
    }
  }

  private static class WatchLastMethodReturnValueAction extends ToggleAction {
    private volatile boolean myWatchesReturnValues;
    private final String myText;
    private final String myTextUnavailable;

    public WatchLastMethodReturnValueAction() {
      super("", DebuggerBundle.message("action.watch.method.return.value.description"), null);
      myWatchesReturnValues = DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
      myText = DebuggerBundle.message("action.watches.method.return.value.enable");
      myTextUnavailable = DebuggerBundle.message("action.watches.method.return.value.unavailable.reason");
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      DebugProcessImpl process = getCurrentDebugProcess(e.getProject());
      if (process == null || process.canGetMethodReturnValue()) {
        presentation.setEnabled(true);
        presentation.setText(myText);
      }
      else {
        presentation.setEnabled(false);
        presentation.setText(myTextUnavailable);
      }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myWatchesReturnValues;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean watch) {
      myWatchesReturnValues = watch;
      DebuggerSettings.getInstance().WATCH_RETURN_VALUES = watch;
      DebugProcessImpl process = getCurrentDebugProcess(e.getProject());
      if (process != null) {
        process.setWatchMethodReturnValuesEnabled(watch);
      }
    }
  }

  @Nullable
  private static DebugProcessImpl getCurrentDebugProcess(@Nullable Project project) {
    if (project != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        XDebugProcess process = session.getDebugProcess();
        if (process instanceof JavaDebugProcess) {
          return ((JavaDebugProcess)process).getDebuggerSession().getProcess();
        }
      }
    }
    return null;
  }

  public NodeManagerImpl getNodeManager() {
    return myNodeManager;
  }

  @Override
  public String getCurrentStateMessage() {
    String description = myJavaSession.getStateDescription();
    return description != null ? description : super.getCurrentStateMessage();
  }

  @Nullable
  @Override
  public XValueMarkerProvider<?, ?> createValueMarkerProvider() {
    return new JavaValueMarker();
  }
}
