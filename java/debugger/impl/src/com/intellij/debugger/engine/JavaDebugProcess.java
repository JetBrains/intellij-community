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
package com.intellij.debugger.engine;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.memory.component.InstancesTracker;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
import com.intellij.debugger.memory.component.MemoryViewManager;
import com.intellij.debugger.memory.ui.ClassesFilteredView;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.AlternativeSourceNotificationProvider;
import com.intellij.debugger.ui.DebuggerContentInfo;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.impl.ThreadsPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeManagerImpl;
import com.intellij.debugger.ui.overhead.OverheadView;
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

/**
 * @author egor
 */
public class JavaDebugProcess extends XDebugProcess {
  private final DebuggerSession myJavaSession;
  private final JavaDebuggerEditorsProvider myEditorsProvider;
  private final XBreakpointHandler<?>[] myBreakpointHandlers;
  private final NodeManagerImpl myNodeManager;

  private static final JavaBreakpointHandlerFactory[] ourDefaultBreakpointHandlerFactories = {
    JavaBreakpointHandler.JavaLineBreakpointHandler::new,
    JavaBreakpointHandler.JavaExceptionBreakpointHandler::new,
    JavaBreakpointHandler.JavaFieldBreakpointHandler::new,
    JavaBreakpointHandler.JavaMethodBreakpointHandler::new,
    JavaBreakpointHandler.JavaWildcardBreakpointHandler::new
  };

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

    myBreakpointHandlers = StreamEx.of(ourDefaultBreakpointHandlerFactories)
      .append(Extensions.getExtensions(JavaBreakpointHandlerFactory.EP_NAME))
      .map(factory -> factory.createHandler(process))
      .toArray(XBreakpointHandler[]::new);

    myJavaSession.getContextManager().addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(@NotNull final DebuggerContextImpl newContext, DebuggerSession.Event event) {
        if (event == DebuggerSession.Event.PAUSE
            || event == DebuggerSession.Event.CONTEXT
            || event == DebuggerSession.Event.REFRESH
            || event == DebuggerSession.Event.REFRESH_WITH_STACK
               && myJavaSession.isPaused()) {
          final SuspendContextImpl newSuspendContext = newContext.getSuspendContext();
          if (newSuspendContext != null &&
              (shouldApplyContext(newContext) || event == DebuggerSession.Event.REFRESH_WITH_STACK)) {
            process.getManagerThread().schedule(new SuspendContextCommandImpl(newSuspendContext) {
              @Override
              public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
                ThreadReferenceProxyImpl threadProxy = newContext.getThreadProxy();
                newSuspendContext.initExecutionStacks(threadProxy);

                Pair<Breakpoint, Event> item = ContainerUtil.getFirstItem(DebuggerUtilsEx.getEventDescriptors(newSuspendContext));
                if (item != null) {
                  XBreakpoint xBreakpoint = item.getFirst().getXBreakpoint();
                  Event second = item.getSecond();
                  if (xBreakpoint != null && second instanceof LocatableEvent &&
                      threadProxy != null && ((LocatableEvent)second).thread() == threadProxy.getThreadReference()) {
                    ((XDebugSessionImpl)getSession()).breakpointReachedNoProcessing(xBreakpoint, newSuspendContext);
                    unsetPausedIfNeeded(newContext);
                    SourceCodeChecker.checkSource(newContext);
                    return;
                  }
                }
                getSession().positionReached(newSuspendContext);
                unsetPausedIfNeeded(newContext);
                SourceCodeChecker.checkSource(newContext);
              }
            });
          }
        }
        else if (event == DebuggerSession.Event.ATTACHED) {
          getSession().rebuildViews(); // to refresh variables views message
        }
      }
    });

    myNodeManager = new NodeManagerImpl(session.getProject(), null) {
      @NotNull
      @Override
      public DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext) {
        return new DebuggerTreeNodeImpl(null, descriptor);
      }

      @Override
      public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
        return new DebuggerTreeNodeImpl(null, descriptor);
      }

      @NotNull
      @Override
      public DebuggerTreeNodeImpl createMessageNode(String message) {
        return new DebuggerTreeNodeImpl(null, new MessageDescriptor(message));
      }
    };
    session.addSessionListener(new XDebugSessionListener() {
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

  private void unsetPausedIfNeeded(DebuggerContextImpl context) {
    SuspendContextImpl suspendContext = context.getSuspendContext();
    if (suspendContext != null && !suspendContext.suspends(context.getThreadProxy())) {
      ((XDebugSessionImpl)getSession()).unsetPaused();
    }
  }

  private boolean shouldApplyContext(DebuggerContextImpl context) {
    SuspendContextImpl suspendContext = context.getSuspendContext();
    SuspendContextImpl currentContext = (SuspendContextImpl)getSession().getSuspendContext();
    if (suspendContext != null && !suspendContext.equals(currentContext)) return true;
    JavaExecutionStack currentExecutionStack = currentContext != null ? currentContext.getActiveExecutionStack() : null;
    return currentExecutionStack == null || !Comparing.equal(context.getThreadProxy(), currentExecutionStack.getThreadProxy());
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
  public void startStepOver(@Nullable XSuspendContext context) {
    myJavaSession.stepOver(false);
  }

  @Override
  public void startStepInto(@Nullable XSuspendContext context) {
    myJavaSession.stepInto(false, null);
  }

  @Override
  public void startForceStepInto(@Nullable XSuspendContext context) {
    myJavaSession.stepInto(true, null);
  }

  @Override
  public void startStepOut(@Nullable XSuspendContext context) {
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
  public void resume(@Nullable XSuspendContext context) {
    myJavaSession.resume();
  }

  @Override
  public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
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
        registerThreadsPanel(ui);
        registerMemoryViewPanel(ui);
        registerOverheadMonitor(ui);
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

      private void registerThreadsPanel(@NotNull RunnerLayoutUi ui) {
        final ThreadsPanel panel = new ThreadsPanel(myJavaSession.getProject(), getDebuggerStateManager());
        final Content threadsContent = ui.createContent(
          DebuggerContentInfo.THREADS_CONTENT, panel, XDebuggerBundle.message("debugger.session.tab.threads.title"),
          AllIcons.Debugger.Threads, null);
        threadsContent.setCloseable(false);
        ui.addContent(threadsContent, 0, PlaceInGrid.left, true);
        ui.addListener(new ContentManagerAdapter() {
          @Override
          public void selectionChanged(ContentManagerEvent event) {
            if (event.getContent() == threadsContent) {
              if (threadsContent.isSelected()) {
                panel.setUpdateEnabled(true);
                if (panel.isRefreshNeeded()) {
                  panel.rebuildIfVisible(DebuggerSession.Event.CONTEXT);
                }
              }
              else {
                panel.setUpdateEnabled(false);
              }
            }
          }
        }, threadsContent);
      }

      private void registerMemoryViewPanel(@NotNull RunnerLayoutUi ui) {
        if (!Registry.is("debugger.enable.memory.view")) return;

        final XDebugSession session = getSession();
        final DebugProcessImpl process = myJavaSession.getProcess();
        final InstancesTracker tracker = InstancesTracker.getInstance(myJavaSession.getProject());

        final ClassesFilteredView classesFilteredView = new ClassesFilteredView(session, process, tracker);

        final Content memoryViewContent =
          ui.createContent(MemoryViewManager.MEMORY_VIEW_CONTENT, classesFilteredView, "Memory View",
                           AllIcons.Debugger.MemoryView.Active, null);

        memoryViewContent.setCloseable(false);
        memoryViewContent.setShouldDisposeContent(true);

        final MemoryViewDebugProcessData data = new MemoryViewDebugProcessData();
        process.putUserData(MemoryViewDebugProcessData.KEY, data);
        session.addSessionListener(new XDebugSessionListener() {
          @Override
          public void sessionStopped() {
            session.removeSessionListener(this);
            data.getTrackedStacks().clear();
          }
        });

        ui.addContent(memoryViewContent, 0, PlaceInGrid.right, true);
        final DebuggerManagerThreadImpl managerThread = process.getManagerThread();
        ui.addListener(new ContentManagerAdapter() {
          @Override
          public void selectionChanged(ContentManagerEvent event) {
            if (event != null && event.getContent() == memoryViewContent) {
              classesFilteredView.setActive(memoryViewContent.isSelected(), managerThread);
            }
          }
        }, memoryViewContent);
      }

      private void registerOverheadMonitor(@NotNull RunnerLayoutUi ui) {
        if (!Registry.is("debugger.enable.overhead.monitor")) return;

        OverheadView monitor = new OverheadView(myJavaSession.getProcess());
        Content overheadContent = ui.createContent("OverheadMonitor", monitor, "Overhead", AllIcons.Debugger.Db_obsolete, null);

        overheadContent.setCloseable(false);
        overheadContent.setShouldDisposeContent(true);

        //session.addSessionListener(new XDebugSessionListener() {
        //  @Override
        //  public void sessionStopped() {
        //    session.removeSessionListener(this);
        //    data.getTrackedStacks().clear();
        //  }
        //});

        ui.addContent(overheadContent, 0, PlaceInGrid.right, true);
        //final DebuggerManagerThreadImpl managerThread = process.getManagerThread();
        //ui.addListener(new ContentManagerAdapter() {
        //  @Override
        //  public void selectionChanged(ContentManagerEvent event) {
        //    if (event != null && event.getContent() == overheadContent) {
        //      classesFilteredView.setActive(overheadContent.isSelected(), managerThread);
        //    }
        //  }
        //}, overheadContent);
      }
    };
  }

  @Override
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar,
                                        @NotNull DefaultActionGroup topToolbar,
                                        @NotNull DefaultActionGroup settings) {
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

  @Override
  public boolean isLibraryFrameFilterSupported() {
    return true;
  }
}
