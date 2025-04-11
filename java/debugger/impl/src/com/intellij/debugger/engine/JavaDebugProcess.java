// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.JvmDropFrameActionHandler;
import com.intellij.debugger.actions.JvmSmartStepIntoActionHandler;
import com.intellij.debugger.actions.ResumeAllJavaThreadsActionHandler;
import com.intellij.debugger.engine.dfaassist.DfaAssist;
import com.intellij.debugger.engine.evaluation.EvaluationContext;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
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
import com.intellij.execution.ui.UIExperiment;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.*;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XDropFrameHandler;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.frame.XValueMarkerProvider;
import com.intellij.xdebugger.impl.ThreadsActionsProvider;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.memory.component.InstancesTracker;
import com.intellij.xdebugger.memory.component.MemoryViewManager;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.intellij.xdebugger.ui.XDebugTabLayouter;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.JavaDebuggerEditorsProvider;

public class JavaDebugProcess extends XDebugProcess {
  private final DebuggerSession myJavaSession;
  private final JavaDebuggerEditorsProvider myEditorsProvider;
  private volatile XBreakpointHandler<?>[] myBreakpointHandlers;
  private final NodeManagerImpl myNodeManager;
  private final JvmSmartStepIntoActionHandler mySmartStepIntoActionHandler;
  private final JvmDropFrameActionHandler myDropFrameActionActionHandler;

  private static final JavaBreakpointHandlerFactory[] ourDefaultBreakpointHandlerFactories = {
    process -> new JavaBreakpointHandler.JavaLineBreakpointHandler(process),
    process -> new JavaBreakpointHandler.JavaExceptionBreakpointHandler(process),
    process -> new JavaBreakpointHandler.JavaFieldBreakpointHandler(process),
    process -> new JavaBreakpointHandler.JavaMethodBreakpointHandler(process),
    process -> new JavaBreakpointHandler.JavaWildcardBreakpointHandler(process),
    process -> new JavaBreakpointHandler.JavaCollectionBreakpointHandler(process)
  };

  public static JavaDebugProcess create(final @NotNull XDebugSession session, final @NotNull DebuggerSession javaSession) {
    JavaDebugProcess res = new JavaDebugProcessWithThreadsActions(session, javaSession);
    javaSession.getProcess().setXDebugProcess(res);
    return res;
  }

  protected JavaDebugProcess(final @NotNull XDebugSession session, final @NotNull DebuggerSession javaSession) {
    super(session);
    myJavaSession = javaSession;
    myEditorsProvider = new JavaDebuggerEditorsProvider();
    final DebugProcessImpl process = javaSession.getProcess();

    myBreakpointHandlers = StreamEx.of(ourDefaultBreakpointHandlerFactories)
      .append(JavaBreakpointHandlerFactory.EP_NAME.getExtensionList().stream())
      .map(factory -> factory.createHandler(process))
      .toArray(XBreakpointHandler[]::new);

    JavaBreakpointHandlerFactory.EP_NAME.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull JavaBreakpointHandlerFactory extension, @NotNull PluginDescriptor pluginDescriptor) {
        //noinspection NonAtomicOperationOnVolatileField
        myBreakpointHandlers = ArrayUtil.append(myBreakpointHandlers, extension.createHandler(myJavaSession.getProcess()));
      }
    }, process.disposable);

    myJavaSession.getContextManager().addListener(new DebuggerContextListener() {
      @Override
      public void changeEvent(final @NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
        if (event == DebuggerSession.Event.CONTEXT) {
          DebuggerSession debuggerSession = newContext.getDebuggerSession();
          ThreadReferenceProxyImpl steppingThreadProxy = newContext.getThreadProxy();
          if (debuggerSession != null && steppingThreadProxy != null && debuggerSession.getState() == DebuggerSession.State.IN_STEPPING) {
            DebugProcessImpl debugProcess = debuggerSession.getProcess();
            debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
              @Override
              protected void action() {
                JavaExecutionStack stack = new JavaExecutionStack(steppingThreadProxy, debugProcess, true);
                getSession().positionReached(new JavaSteppingSuspendContext(debugProcess, stack));
              }
            });
            return;
          }
        }

        if (event == DebuggerSession.Event.PAUSE
            || event == DebuggerSession.Event.CONTEXT
            || event == DebuggerSession.Event.REFRESH
            || event == DebuggerSession.Event.REFRESH_WITH_STACK
               && myJavaSession.isPaused()) {
          final SuspendContextImpl newSuspendContext = newContext.getSuspendContext();
          if (newSuspendContext != null &&
              (shouldApplyContext(newContext) || event == DebuggerSession.Event.REFRESH_WITH_STACK)) {
            newSuspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(newSuspendContext) {
              @Override
              public void contextAction(@NotNull SuspendContextImpl suspendContext) {
                ThreadReferenceProxyImpl threadProxy = newContext.getThreadProxy();
                newSuspendContext.initExecutionStacks(threadProxy);

                if (event == DebuggerSession.Event.REFRESH) {
                  ((XDebugSessionImpl)getSession()).updateSuspendContext(newSuspendContext);
                  return;
                }

                Pair<Breakpoint, Event> item = ContainerUtil.getFirstItem(DebuggerUtilsEx.getEventDescriptors(newSuspendContext));
                if (item != null) {
                  XBreakpoint xBreakpoint = item.getFirst().getXBreakpoint();
                  Event second = item.getSecond();
                  if (xBreakpoint != null && second instanceof LocatableEvent &&
                      threadProxy != null && ((LocatableEvent)second).thread() == threadProxy.getThreadReference()) {
                    ((XDebugSessionImpl)getSession()).breakpointReachedNoProcessing(xBreakpoint, newSuspendContext);
                    SourceCodeChecker.checkSource(newContext);
                    return;
                  }
                }
                getSession().positionReached(newSuspendContext);
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
      @Override
      public @NotNull DebuggerTreeNodeImpl createNode(final NodeDescriptor descriptor, EvaluationContext evaluationContext) {
        return new DebuggerTreeNodeImpl(null, descriptor);
      }

      @Override
      public DebuggerTreeNodeImpl createMessageNode(MessageDescriptor descriptor) {
        return new DebuggerTreeNodeImpl(null, descriptor);
      }

      @Override
      public @NotNull DebuggerTreeNodeImpl createMessageNode(String message) {
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
            if (!AlternativeSourceNotificationProvider.isFileProcessed(file)) {
              EditorNotifications.getInstance(session.getProject()).updateNotifications(file);
            }
          }
        }
      }
    });
    if (!DebuggerUtilsImpl.isRemote(process)) {
      DfaAssist.installDfaAssist(myJavaSession, session, process.disposable);
    }

    mySmartStepIntoActionHandler = new JvmSmartStepIntoActionHandler(javaSession);
    myDropFrameActionActionHandler = new JvmDropFrameActionHandler(javaSession);
  }

  private boolean shouldApplyContext(DebuggerContextImpl context) {
    SuspendContextImpl suspendContext = context.getSuspendContext();
    if (getSession().getSuspendContext() instanceof SuspendContextImpl currentContext) {
      if (suspendContext == null || suspendContext.equals(currentContext)) {
        JavaExecutionStack currentExecutionStack = currentContext.getActiveExecutionStack();
        return currentExecutionStack == null || !Comparing.equal(context.getThreadProxy(), currentExecutionStack.getThreadProxy());
      }
    }
    return true;
  }

  public void saveNodeHistory() {
    saveNodeHistory(getDebuggerStateManager().getContext().getFrameProxy());
  }

  private void saveNodeHistory(final StackFrameProxyImpl frameProxy) {
    myJavaSession.getProcess().getManagerThread().schedule(PrioritizedTask.Priority.NORMAL,
                                                           () -> myNodeManager.setHistoryByContext(frameProxy));
  }

  private DebuggerStateManager getDebuggerStateManager() {
    return myJavaSession.getContextManager();
  }

  public DebuggerSession getDebuggerSession() {
    return myJavaSession;
  }

  @Override
  public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
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

  @Override
  public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
    return myBreakpointHandlers;
  }

  @Override
  public boolean checkCanInitBreakpoints() {
    return false;
  }

  @Override
  protected @Nullable ProcessHandler doGetProcessHandler() {
    return myJavaSession.getProcess().getProcessHandler();
  }

  @Override
  public @NotNull ExecutionConsole createConsole() {
    ExecutionConsole console = myJavaSession.getProcess().getExecutionResult().getExecutionConsole();
    if (console != null) return console;
    return super.createConsole();
  }

  @Override
  public @NotNull XDebugTabLayouter createTabLayouter() {
    return new XDebugTabLayouter() {
      @Override
      public void registerAdditionalContent(@NotNull RunnerLayoutUi ui) {
        registerThreadsPanel(ui);
        registerMemoryViewPanel(ui);
        registerOverheadMonitor(ui);
      }

      @Override
      public @NotNull Content registerConsoleContent(@NotNull RunnerLayoutUi ui, @NotNull ExecutionConsole console) {
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
          null, panel.getDefaultFocusedComponent());
        threadsContent.setCloseable(false);
        ui.addContent(threadsContent, 0, PlaceInGrid.left, true);
        ui.addListener(new ContentManagerListener() {
          @Override
          public void selectionChanged(@NotNull ContentManagerEvent event) {
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
          ui.createContent(MemoryViewManager.MEMORY_VIEW_CONTENT, classesFilteredView, JavaDebuggerBundle.message("memory.toolwindow.title"),
                           null, classesFilteredView.getDefaultFocusedComponent());

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
        ui.addListener(new ContentManagerListener() {
          @Override
          public void selectionChanged(@NotNull ContentManagerEvent event) {
            if (event.getContent() == memoryViewContent) {
              classesFilteredView.setActive(memoryViewContent.isSelected(), managerThread);
            }
          }
        }, memoryViewContent);
      }

      private void registerOverheadMonitor(@NotNull RunnerLayoutUi ui) {
        if (!Registry.is("debugger.enable.overhead.monitor")) return;

        DebugProcessImpl process = myJavaSession.getProcess();
        OverheadView monitor = new OverheadView(process);
        Content overheadContent = ui.createContent("OverheadMonitor", monitor, JavaDebuggerBundle.message("overhead.toolwindow.title"), null, monitor.getDefaultFocusedComponent());

        monitor.setBouncer(() -> ui.setBouncing(overheadContent, true));

        overheadContent.setCloseable(false);
        overheadContent.setShouldDisposeContent(true);

        ui.addContent(overheadContent, 0, PlaceInGrid.right, true);
      }
    };
  }

  @Override
  public void registerAdditionalActions(@NotNull DefaultActionGroup leftToolbar,
                                        @NotNull DefaultActionGroup topToolbar,
                                        @NotNull DefaultActionGroup settings) {
    if (!UIExperiment.isNewDebuggerUIEnabled()) {
      Constraints beforeRunner = new Constraints(Anchor.BEFORE, "Runner.Layout");
      leftToolbar.add(Separator.getInstance(), beforeRunner);
      leftToolbar.add(ActionManager.getInstance().getAction("DumpThreads"), beforeRunner);
      leftToolbar.add(Separator.getInstance(), beforeRunner);
    }

    Constraints beforeSort = new Constraints(Anchor.BEFORE, "XDebugger.ToggleSortValues");
    settings.addAction(new WatchLastMethodReturnValueAction(), beforeSort);
    settings.addAction(new AutoVarsSwitchAction(), beforeSort);
  }

  private static class AutoVarsSwitchAction extends ToggleAction {
    private volatile boolean myAutoModeEnabled;

    AutoVarsSwitchAction() {
      super(JavaDebuggerBundle.message("action.auto.variables.mode"), JavaDebuggerBundle.message("action.auto.variables.mode.description"), null);
      myAutoModeEnabled = DebuggerSettings.getInstance().AUTO_VARIABLES_MODE;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myAutoModeEnabled;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean enabled) {
      myAutoModeEnabled = enabled;
      DebuggerSettings.getInstance().AUTO_VARIABLES_MODE = enabled;
      XDebuggerUtilImpl.rebuildAllSessionsViews(e.getProject());
    }
  }

  private static class WatchLastMethodReturnValueAction extends ToggleAction {
    private final @NlsActions.ActionText String myText;
    private final @NlsActions.ActionText String myTextUnavailable;

    WatchLastMethodReturnValueAction() {
      super("", JavaDebuggerBundle.message("action.watch.method.return.value.description"), null);
      myText = JavaDebuggerBundle.message("action.watches.method.return.value.enable");
      myTextUnavailable = JavaDebuggerBundle.message("action.watches.method.return.value.unavailable.reason");
    }

    @Override
    public void update(final @NotNull AnActionEvent e) {
      super.update(e);
      final Presentation presentation = e.getPresentation();
      DebugProcessImpl process = getCurrentDebugProcess(e);
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return DebuggerSettings.getInstance().WATCH_RETURN_VALUES;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean watch) {
      DebuggerSettings.getInstance().WATCH_RETURN_VALUES = watch;
      DebugProcessImpl process = getCurrentDebugProcess(e);
      if (process != null) {
        process.setWatchMethodReturnValuesEnabled(watch);
      }
    }
  }

  public static @Nullable DebugProcessImpl getCurrentDebugProcess(@NotNull AnActionEvent e) {
    XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session != null) {
      XDebugProcess process = session.getDebugProcess();
      if (process instanceof JavaDebugProcess) {
        return ((JavaDebugProcess)process).getDebuggerSession().getProcess();
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

  @Override
  public @Nullable XValueMarkerProvider<?, ?> createValueMarkerProvider() {
    return new JavaValueMarker();
  }

  @Override
  public boolean isLibraryFrameFilterSupported() {
    return true;
  }

  @Override
  public @Nullable XSmartStepIntoHandler<?> getSmartStepIntoHandler() {
    return mySmartStepIntoActionHandler;
  }

  @ApiStatus.Experimental
  @Override
  public @Nullable XDropFrameHandler getDropFrameHandler() {
    return myDropFrameActionActionHandler;
  }

  private static final class JavaDebugProcessWithThreadsActions extends JavaDebugProcess implements ThreadsActionsProvider {
    private JavaDebugProcessWithThreadsActions(@NotNull XDebugSession session, @NotNull DebuggerSession javaSession) {
      super(session, javaSession);
      myResumeAllJavaThreadsActionHandler = new ResumeAllJavaThreadsActionHandler(getDebuggerSession().getProcess());
    }

    private final ResumeAllJavaThreadsActionHandler myResumeAllJavaThreadsActionHandler;

    @Override
    public @Nullable DebuggerActionHandler getThawAllThreadsHandler() {
      return myResumeAllJavaThreadsActionHandler;
    }
  }
}
