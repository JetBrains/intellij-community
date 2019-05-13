// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.Stream;

@State(name = "DebuggerManager", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class DebuggerManagerImpl extends DebuggerManagerEx implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerManagerImpl");
  public static final String LOCALHOST_ADDRESS_FALLBACK = "127.0.0.1";

  private final Project myProject;
  private final HashMap<ProcessHandler, DebuggerSession> mySessions = new HashMap<>();
  private final BreakpointManager myBreakpointManager;
  private final List<NameMapper> myNameMappers = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<Function<DebugProcess, PositionManager>> myCustomPositionManagerFactories = new SmartList<>();

  private final EventDispatcher<DebuggerManagerListener> myDispatcher = EventDispatcher.create(DebuggerManagerListener.class);
  private final MyDebuggerStateManager myDebuggerStateManager = new MyDebuggerStateManager();

  private final DebuggerContextListener mySessionListener = new DebuggerContextListener() {
    @Override
    public void changeEvent(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {

      final DebuggerSession session = newContext.getDebuggerSession();
      if (event == DebuggerSession.Event.PAUSE && myDebuggerStateManager.myDebuggerSession != session) {
        // if paused in non-active session; switch current session
        myDebuggerStateManager.setState(newContext, session != null? session.getState() : DebuggerSession.State.DISPOSED, event, null);
        return;
      }

      if (myDebuggerStateManager.myDebuggerSession == session) {
        myDebuggerStateManager.fireStateChanged(newContext, event);
      }
      if (event == DebuggerSession.Event.ATTACHED) {
        myDispatcher.getMulticaster().sessionAttached(session);
      }
      else if (event == DebuggerSession.Event.DETACHED) {
        myDispatcher.getMulticaster().sessionDetached(session);
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

  @Override
  public void addClassNameMapper(final NameMapper mapper) {
    myNameMappers.add(mapper);
  }

  @Override
  public void removeClassNameMapper(final NameMapper mapper) {
    myNameMappers.remove(mapper);
  }

  @Override
  public String getVMClassQualifiedName(@NotNull final PsiClass aClass) {
    for (NameMapper nameMapper : myNameMappers) {
      final String qName = nameMapper.getQualifiedName(aClass);
      if (qName != null) {
        return qName;
      }
    }
    return aClass.getQualifiedName();
  }

  @Override
  public void addDebuggerManagerListener(DebuggerManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void removeDebuggerManagerListener(DebuggerManagerListener listener) {
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
    }
    myBreakpointManager.addListeners(busConnection);
  }

  @Nullable
  @Override
  public DebuggerSession getSession(DebugProcess process) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return getSessions().stream().filter(debuggerSession -> process == debuggerSession.getProcess()).findFirst().orElse(null);
  }

  @NotNull
  @Override
  public Collection<DebuggerSession> getSessions() {
    synchronized (mySessions) {
      final Collection<DebuggerSession> values = mySessions.values();
      return values.isEmpty() ? Collections.emptyList() : new ArrayList<>(values);
    }
  }

  @Nullable
  @Override
  public Element getState() {
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

  /**
   * @deprecated to be removed with {@link DebuggerManager#registerPositionManagerFactory(Function)}
   */
  @Deprecated
  public Stream<Function<DebugProcess, PositionManager>> getCustomPositionManagerFactories() {
    return myCustomPositionManagerFactories.stream();
  }

  @Override
  @Nullable
  public DebuggerSession attachVirtualMachine(@NotNull DebugEnvironment environment) throws ExecutionException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    DebugProcessEvents debugProcess = new DebugProcessEvents(myProject);
    DebuggerSession session = DebuggerSession.create(debugProcess, environment);
    ExecutionResult executionResult = session.getProcess().getExecutionResult();
    if (executionResult == null) {
      return null;
    }
    session.getContextManager().addListener(mySessionListener);
    getContextManager()
      .setState(DebuggerContextUtil.createDebuggerContext(session, session.getContextManager().getContext().getSuspendContext()),
                session.getState(), DebuggerSession.Event.CONTEXT, null);

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
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
          ProcessHandler processHandler = event.getProcessHandler();
          final DebugProcessImpl debugProcess = getDebugProcess(processHandler);
          if (debugProcess != null) {
            // if current thread is a "debugger manager thread", stop will execute synchronously
            // it is KillableColoredProcessHandler responsibility to terminate VM
            debugProcess.stop(willBeDestroyed && !(processHandler instanceof KillableColoredProcessHandler && ((KillableColoredProcessHandler)processHandler).shouldKillProcessSoftly()));

            // wait at most 10 seconds: the problem is that debugProcess.stop() can hang if there are troubles in the debuggee
            // if processWillTerminate() is called from AWT thread debugProcess.waitFor() will block it and the whole app will hang
            if (!DebuggerManagerThreadImpl.isManagerThread()) {
              if (SwingUtilities.isEventDispatchThread()) {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                  ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
                  debugProcess.waitFor(10000);
                }, "Waiting For Debugger Response", false, debugProcess.getProject());
              }
              else {
                debugProcess.waitFor(10000);
              }
            }
          }
        }
      });
    }
    myDispatcher.getMulticaster().sessionCreated(session);

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
  @Nullable
  public DebuggerSession getDebugSession(final ProcessHandler processHandler) {
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
      processHandler.addProcessListener(new ProcessAdapter() {
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
      processHandler.addProcessListener(new ProcessAdapter() {
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

  @NotNull
  @Override
  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  @NotNull
  @Override
  public DebuggerContextImpl getContext() {
    return getContextManager().getContext();
  }

  @NotNull
  @Override
  public DebuggerStateManager getContextManager() {
    return myDebuggerStateManager;
  }

  @Override
  public void registerPositionManagerFactory(final Function<DebugProcess, PositionManager> factory) {
    myCustomPositionManagerFactories.add(factory);
  }

  /**
   * @deprecated use {@link RemoteConnectionBuilder}
   */
  @Deprecated
  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean debuggerInServerMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity) throws ExecutionException {
    return createDebugParameters(parameters, debuggerInServerMode, transport, debugPort, checkValidity, true);
  }

  /**
   * @deprecated use {@link RemoteConnectionBuilder}
   */
  @Deprecated
  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean debuggerInServerMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity,
                                                       boolean addAsyncDebuggerAgent)
    throws ExecutionException {
    return new RemoteConnectionBuilder(debuggerInServerMode, transport, debugPort)
      .checkValidity(checkValidity)
      .asyncAgent(addAsyncDebuggerAgent)
      .memoryAgent(Registry.is("debugger.enable.memory.agent"))
      .create(parameters);
  }

  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       GenericDebuggerRunnerSettings settings,
                                                       boolean checkValidity)
    throws ExecutionException {
    return createDebugParameters(parameters, settings.LOCAL, settings.getTransport(), settings.getDebugPort(), checkValidity);
  }

  private static class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerSession myDebuggerSession;

    @NotNull
    @Override
    public DebuggerContextImpl getContext() {
      return myDebuggerSession == null ? DebuggerContextImpl.EMPTY_CONTEXT : myDebuggerSession.getContextManager().getContext();
    }

    @Override
    public void setState(@NotNull final DebuggerContextImpl context, DebuggerSession.State state, DebuggerSession.Event event, String description) {
      ApplicationManager.getApplication().assertIsDispatchThread();
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
      myDispatcher.getMulticaster().sessionRemoved(session);
    }
  }
}
