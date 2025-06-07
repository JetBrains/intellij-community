// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.DebugEnvironment;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.NameMapper;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManagerListener;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "DebuggerManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class DebuggerManagerImpl extends DebuggerManagerEx implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(DebuggerManagerImpl.class);
  public static final String LOCALHOST_ADDRESS_FALLBACK = "127.0.0.1";
  private static final int WAIT_KILL_TIMEOUT = 10000;

  private final Project myProject;
  private final Map<ProcessHandler, DebuggerSession> mySessions = new HashMap<>();
  private final BreakpointManager myBreakpointManager;
  private final List<NameMapper> myNameMappers = ContainerUtil.createLockFreeCopyOnWriteList();

  private final EventDispatcher<DebuggerManagerListener> myDispatcher = EventDispatcher.create(DebuggerManagerListener.class);
  private final MyDebuggerStateManager myDebuggerStateManager = new MyDebuggerStateManager();

  private final DebuggerContextListener mySessionListener = new DebuggerContextListener() {
    @Override
    public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
      final DebuggerSession session = newContext.getDebuggerSession();
      if (event == DebuggerSession.Event.PAUSE && myDebuggerStateManager.myDebuggerSession != session) {
        // if paused in non-active session; switch current session
        myDebuggerStateManager.setState(newContext, session != null ? session.getState() : DebuggerSession.State.DISPOSED, event, null);
        return;
      }

      if (myDebuggerStateManager.myDebuggerSession == session) {
        myDebuggerStateManager.fireStateChanged(newContext, event);
      }
      if (event == DebuggerSession.Event.ATTACHED) {
        getEventPublisher().sessionAttached(session);
      }
      else if (event == DebuggerSession.Event.DETACHED) {
        getEventPublisher().sessionDetached(session);
      }
      else if (event == DebuggerSession.Event.DISPOSE) {
        dispose(session);
        if (myDebuggerStateManager.myDebuggerSession == session) {
          myDebuggerStateManager
            .setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.State.DISPOSED, DebuggerSession.Event.DISPOSE, null);
        }
      }
    }
  };

  private @NotNull DebuggerManagerListener getEventPublisher() {
    return myProject.getMessageBus().syncPublisher(DebuggerManagerListener.TOPIC);
  }

  @Override
  public void addClassNameMapper(final NameMapper mapper) {
    myNameMappers.add(mapper);
  }

  @Override
  public void removeClassNameMapper(final NameMapper mapper) {
    myNameMappers.remove(mapper);
  }

  @Override
  public String getVMClassQualifiedName(final @NotNull PsiClass aClass) {
    for (NameMapper nameMapper : myNameMappers) {
      final String qName = nameMapper.getQualifiedName(aClass);
      if (qName != null) {
        return qName;
      }
    }
    return aClass.getQualifiedName();
  }

  @Override
  public void addDebuggerManagerListener(@NotNull DebuggerManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeDebuggerManagerListener(@NotNull DebuggerManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public DebuggerManagerImpl(@NotNull Project project) {
    myProject = project;
    myBreakpointManager = new BreakpointManager(myProject, this);
    MessageBusConnection busConnection = project.getMessageBus().connect();
    if (!project.isDefault()) {
      busConnection.subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
        @Override
        public void globalSchemeChange(EditorColorsScheme scheme) {
          getBreakpointManager().updateBreakpointsUI();
        }
      });

      busConnection.subscribe(DebuggerManagerListener.TOPIC, myDispatcher.getMulticaster());
    }
    myBreakpointManager.addListeners(busConnection);
  }

  @Override
  public @Nullable DebuggerSession getSession(DebugProcess process) {
    ThreadingAssertions.assertEventDispatchThread();
    return ContainerUtil.find(getSessions(), debuggerSession -> process == debuggerSession.getProcess());
  }

  @Override
  public @NotNull Collection<DebuggerSession> getSessions() {
    synchronized (mySessions) {
      final Collection<DebuggerSession> values = mySessions.values();
      return values.isEmpty() ? Collections.emptyList() : new ArrayList<>(values);
    }
  }

  @Override
  public @Nullable Element getState() {
    Element state = new Element("state");
    myBreakpointManager.writeExternal(state);
    return state;
  }

  @Override
  public void loadState(@NotNull Element state) {
    myBreakpointManager.readExternal(state);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myBreakpointManager.writeExternal(element);
  }

  @Override
  public @Nullable DebuggerSession attachVirtualMachine(@NotNull DebugEnvironment environment) throws ExecutionException {
    DebugProcessEvents debugProcess = new DebugProcessEvents(myProject);
    DebuggerSession session = DebuggerSession.create(debugProcess, environment);
    ExecutionResult executionResult = session.getProcess().getExecutionResult();
    if (executionResult == null) {
      return null;
    }
    session.getContextManager().addListener(mySessionListener);

    // the whole method may still be called from EDT, we need to update the state immediately in this case
    UIUtil.invokeLaterIfNeeded(() -> {
      getContextManager()
        .setState(DebuggerContextUtil.createDebuggerContext(session, session.getContextManager().getContext().getSuspendContext()),
                  session.getState(), DebuggerSession.Event.CONTEXT, null);
    });

    final ProcessHandler processHandler = executionResult.getProcessHandler();

    synchronized (mySessions) {
      mySessions.put(processHandler, session);
    }

    if (!(processHandler instanceof RemoteDebugProcessHandler)) {
      // add listener only to non-remote process handler:
      // on Unix systems destroying process does not cause VMDeathEvent to be generated,
      // so we need to call debugProcess.stop() explicitly for graceful termination.
      // RemoteProcessHandler on the other hand will call debugProcess.stop() as a part of destroyProcess() and detachProcess() implementation,
      // so we shouldn't add the listener to avoid calling stop() twice
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
          ProcessHandler processHandler = event.getProcessHandler();
          final DebugProcessImpl debugProcess = getDebugProcess(processHandler);
          if (debugProcess != null) {
            if (Registry.is("debugger.stop.on.graceful.exit")) {
              // it is KillableColoredProcessHandler responsibility to terminate VM
              debugProcess.stop(willBeDestroyed &&
                                !(processHandler instanceof KillableColoredProcessHandler &&
                                  ((KillableColoredProcessHandler)processHandler).shouldKillProcessSoftly()));

              // still need to wait in tests for results stability
              if (ApplicationManager.getApplication().isUnitTestMode()) {
                assert !DebuggerManagerThreadImpl.isManagerThread() && !DebuggerManagerThreadImpl.isManagerThread();
                debugProcess.waitFor(WAIT_KILL_TIMEOUT);
              }
            }
          }
        }

        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          DebugProcessImpl debugProcess = getDebugProcess(event.getProcessHandler());
          if (debugProcess != null) {
            debugProcess.stop(false);
          }
        }
      });
    }
    getEventPublisher().sessionCreated(session);

    if (debugProcess.isDetached() || debugProcess.isDetaching()) {
      session.dispose();
      return null;
    }
    if (environment.isRemote()) {
      // optimization: that way BatchEvaluator will not try to lookup the class file in remote VM
      // which is an expensive operation when executed first time
      debugProcess.putUserData(BatchEvaluator.REMOTE_SESSION_KEY, Boolean.TRUE);
    }

    return session;
  }

  @Override
  public DebugProcessImpl getDebugProcess(final ProcessHandler processHandler) {
    synchronized (mySessions) {
      DebuggerSession session = mySessions.get(processHandler);
      return session != null ? session.getProcess() : null;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public @Nullable DebuggerSession getDebugSession(final ProcessHandler processHandler) {
    synchronized (mySessions) {
      return mySessions.get(processHandler);
    }
  }

  @Override
  public void addDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.addDebugProcessListener(listener);
    }
    else {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void startNotified(@NotNull ProcessEvent event) {
          DebugProcessImpl debugProcess = getDebugProcess(processHandler);
          if (debugProcess != null) {
            debugProcess.addDebugProcessListener(listener);
          }
          processHandler.removeProcessListener(this);
        }
      });
    }
  }

  @Override
  public void removeDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.removeDebugProcessListener(listener);
    }
    else {
      processHandler.addProcessListener(new ProcessListener() {
        @Override
        public void startNotified(@NotNull ProcessEvent event) {
          DebugProcessImpl debugProcess = getDebugProcess(processHandler);
          if (debugProcess != null) {
            debugProcess.removeDebugProcessListener(listener);
          }
          processHandler.removeProcessListener(this);
        }
      });
    }
  }

  @Override
  public boolean isDebuggerManagerThread() {
    return DebuggerManagerThreadImpl.isManagerThread();
  }

  @Override
  public @NotNull BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  @Override
  public @NotNull DebuggerContextImpl getContext() {
    return getContextManager().getContext();
  }

  @Override
  public @NotNull DebuggerStateManager getContextManager() {
    return myDebuggerStateManager;
  }

  /**
   * @deprecated use {@link RemoteConnectionBuilder}
   */
  @Deprecated
  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean debuggerInServerMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity) throws ExecutionException {
    return new RemoteConnectionBuilder(debuggerInServerMode, transport, debugPort)
      .checkValidity(checkValidity)
      .asyncAgent(true)
      .create(parameters);
  }

  private static class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerSession myDebuggerSession;

    @Override
    public @NotNull DebuggerContextImpl getContext() {
      return myDebuggerSession == null ? DebuggerContextImpl.EMPTY_CONTEXT : myDebuggerSession.getContextManager().getContext();
    }

    @Override
    public void setState(final @NotNull DebuggerContextImpl context,
                         DebuggerSession.State state,
                         DebuggerSession.Event event,
                         String description) {
      ThreadingAssertions.assertEventDispatchThread();
      myDebuggerSession = context.getDebuggerSession();
      if (myDebuggerSession != null) {
        myDebuggerSession.getContextManager().setState(context, state, event, description);
      }
      else {
        fireStateChanged(context, event);
      }
    }
  }

  private void dispose(DebuggerSession session) {
    ProcessHandler processHandler = session.getProcess().getProcessHandler();
    synchronized (mySessions) {
      DebuggerSession removed = mySessions.remove(processHandler);
      LOG.assertTrue(removed != null);
      getEventPublisher().sessionRemoved(session);
    }
  }

  public static class DebuggerRunContentWithExecutorListener implements XDebuggerManagerListener {
    private final Project myProject;

    public DebuggerRunContentWithExecutorListener(Project project) {
      myProject = project;
    }

    @Override
    public void currentSessionChanged(@Nullable XDebugSession previousSession, @Nullable XDebugSession currentSession) {
      if (currentSession != null) {
        DebuggerSession session =
          currentSession.getDebugProcess() instanceof JavaDebugProcess javaDebugProcess ? javaDebugProcess.getDebuggerSession() : null;
        DebuggerStateManager manager = getInstanceEx(myProject).getContextManager();
        DebuggerInvocationUtil.invokeLater(myProject, () -> {
          if (session != null) {
            manager.setState(session.getContextManager().getContext(), session.getState(), DebuggerSession.Event.CONTEXT, null);
          }
          else {
            manager.setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.State.DISPOSED, DebuggerSession.Event.CONTEXT, null);
          }
        });
      }
    }
  }
}
