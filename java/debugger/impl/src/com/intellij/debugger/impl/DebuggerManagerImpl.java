/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.debugger.*;
import com.intellij.debugger.apiAdapters.TransportServiceWrapper;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.GetJPDADialog;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.jar.Attributes;

@State(name = "DebuggerManager", storages = {@Storage(file = StoragePathMacros.WORKSPACE_FILE)})
public class DebuggerManagerImpl extends DebuggerManagerEx implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerManagerImpl");

  private final Project myProject;
  private final HashMap<ProcessHandler, DebuggerSession> mySessions = new HashMap<ProcessHandler, DebuggerSession>();
  private final BreakpointManager myBreakpointManager;
  private final List<NameMapper> myNameMappers = ContainerUtil.createLockFreeCopyOnWriteList();
  private final List<Function<DebugProcess, PositionManager>> myCustomPositionManagerFactories =
    new ArrayList<Function<DebugProcess, PositionManager>>();

  private final EventDispatcher<DebuggerManagerListener> myDispatcher = EventDispatcher.create(DebuggerManagerListener.class);
  private final MyDebuggerStateManager myDebuggerStateManager = new MyDebuggerStateManager();

  private final DebuggerContextListener mySessionListener = new DebuggerContextListener() {
    @Override
    public void changeEvent(DebuggerContextImpl newContext, int event) {

      final DebuggerSession session = newContext.getDebuggerSession();
      if (event == DebuggerSession.EVENT_PAUSE && myDebuggerStateManager.myDebuggerSession != session) {
        // if paused in non-active session; switch current session
        myDebuggerStateManager.setState(newContext, session != null? session.getState() : DebuggerSession.STATE_DISPOSED, event, null);
        return;
      }

      if (myDebuggerStateManager.myDebuggerSession == session) {
        myDebuggerStateManager.fireStateChanged(newContext, event);
      }
      if (event == DebuggerSession.EVENT_ATTACHED) {
        myDispatcher.getMulticaster().sessionAttached(session);
      }
      else if (event == DebuggerSession.EVENT_DETACHED) {
        myDispatcher.getMulticaster().sessionDetached(session);
      }
      else if (event == DebuggerSession.EVENT_DISPOSE) {
        dispose(session);
        if (myDebuggerStateManager.myDebuggerSession == session) {
          myDebuggerStateManager
            .setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_DISPOSE, null);
        }
      }
    }
  };
  @NonNls private static final String DEBUG_KEY_NAME = "idea.xdebug.key";

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

  public DebuggerManagerImpl(Project project, StartupManager startupManager, EditorColorsManager colorsManager) {
    myProject = project;
    myBreakpointManager = new BreakpointManager(myProject, startupManager, this);
    if (!project.isDefault()) {
      colorsManager.addEditorColorsListener(new EditorColorsListener() {
        @Override
        public void globalSchemeChange(EditorColorsScheme scheme) {
          getBreakpointManager().updateBreakpointsUI();
        }
      }, project);
    }
  }

  @Override
  public DebuggerSession getSession(DebugProcess process) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (final DebuggerSession debuggerSession : getSessions()) {
      if (process == debuggerSession.getProcess()) return debuggerSession;
    }
    return null;
  }

  @Override
  public Collection<DebuggerSession> getSessions() {
    synchronized (mySessions) {
      final Collection<DebuggerSession> values = mySessions.values();
      return values.isEmpty() ? Collections.<DebuggerSession>emptyList() : new ArrayList<DebuggerSession>(values);
    }
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void projectOpened() {
    myBreakpointManager.init();
  }

  @Nullable
  @Override
  public Element getState() {
    Element state = new Element("state");
    myBreakpointManager.writeExternal(state);
    return state;
  }

  @Override
  public void loadState(Element state) {
    myBreakpointManager.readExternal(state);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myBreakpointManager.writeExternal(element);
  }

  @Override
  public DebuggerSession attachVirtualMachine(Executor executor,
                                              ProgramRunner runner,
                                              ModuleRunProfile profile,
                                              RunProfileState state,
                                              RemoteConnection remoteConnection,
                                              boolean pollConnection
  ) throws ExecutionException {
    return attachVirtualMachine(new DefaultDebugEnvironment(myProject,
                                                            executor,
                                                            runner,
                                                            profile,
                                                            state,
                                                            remoteConnection,
                                                            pollConnection));
  }

  @Override
  public DebuggerSession attachVirtualMachine(DebugEnvironment environment) throws ExecutionException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final DebugProcessEvents debugProcess = new DebugProcessEvents(myProject);
    debugProcess.addDebugProcessListener(new DebugProcessAdapter() {
      @Override
      public void processAttached(final DebugProcess process) {
        process.removeDebugProcessListener(this);
        for (Function<DebugProcess, PositionManager> factory : myCustomPositionManagerFactories) {
          final PositionManager positionManager = factory.fun(process);
          if (positionManager != null) {
            process.appendPositionManager(positionManager);
          }
        }
        for (PositionManagerFactory factory : Extensions.getExtensions(PositionManagerFactory.EP_NAME, myProject)) {
          final PositionManager manager = factory.createPositionManager(debugProcess);
          if (manager != null) {
            process.appendPositionManager(manager);
          }
        }
      }

      @Override
      public void processDetached(final DebugProcess process, final boolean closedByUser) {
        debugProcess.removeDebugProcessListener(this);
      }

      @Override
      public void attachException(final RunProfileState state,
                                  final ExecutionException exception,
                                  final RemoteConnection remoteConnection) {
        debugProcess.removeDebugProcessListener(this);
      }
    });
    final DebuggerSession session = new DebuggerSession(environment.getSessionName(), debugProcess);

    final ExecutionResult executionResult = session.attach(environment);
    if (executionResult == null) {
      return null;
    }
    session.getContextManager().addListener(mySessionListener);
    getContextManager()
      .setState(DebuggerContextUtil.createDebuggerContext(session, session.getContextManager().getContext().getSuspendContext()),
                session.getState(), DebuggerSession.EVENT_CONTEXT, null);

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
        public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
          final DebugProcessImpl debugProcess = getDebugProcess(event.getProcessHandler());
          if (debugProcess != null) {
            // if current thread is a "debugger manager thread", stop will execute synchronously
            debugProcess.stop(willBeDestroyed);

            // wait at most 10 seconds: the problem is that debugProcess.stop() can hang if there are troubles in the debuggee
            // if processWillTerminate() is called from AWT thread debugProcess.waitFor() will block it and the whole app will hang
            if (!DebuggerManagerThreadImpl.isManagerThread()) {
              debugProcess.waitFor(10000);
            }
          }
        }
      });
    }
    myDispatcher.getMulticaster().sessionCreated(session);
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
        public void startNotified(ProcessEvent event) {
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
        public void startNotified(ProcessEvent event) {
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
  @NotNull
  public String getComponentName() {
    return "DebuggerManager";
  }

  @Override
  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  @Override
  public DebuggerContextImpl getContext() {
    return getContextManager().getContext();
  }

  @Override
  public DebuggerStateManager getContextManager() {
    return myDebuggerStateManager;
  }

  @Override
  public void registerPositionManagerFactory(final Function<DebugProcess, PositionManager> factory) {
    myCustomPositionManagerFactories.add(factory);
  }

  @Override
  public void unregisterPositionManagerFactory(final Function<DebugProcess, PositionManager> factory) {
    myCustomPositionManagerFactories.remove(factory);
  }

  private static boolean hasWhitespace(String string) {
    int length = string.length();
    for (int i = 0; i < length; i++) {
      if (Character.isWhitespace(string.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /* Remoting */
  private static void checkTargetJPDAInstalled(JavaParameters parameters) throws ExecutionException {
    final Sdk jdk = parameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(DebuggerBundle.message("error.jdk.not.specified"));
    }
    final JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    String versionString = jdk.getVersionString();
    if (version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1) {
      throw new ExecutionException(DebuggerBundle.message("error.unsupported.jdk.version", versionString));
    }
    if (SystemInfo.isWindows && version == JavaSdkVersion.JDK_1_2) {
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null || !homeDirectory.isValid()) {
        throw new ExecutionException(DebuggerBundle.message("error.invalid.jdk.home", versionString));
      }
      //noinspection HardCodedStringLiteral
      File dllFile = new File(
        homeDirectory.getPath().replace('/', File.separatorChar) + File.separator + "bin" + File.separator + "jdwp.dll"
      );
      if (!dllFile.exists()) {
        GetJPDADialog dialog = new GetJPDADialog();
        dialog.show();
        throw new ExecutionException(DebuggerBundle.message("error.debug.libraries.missing"));
      }
    }
  }

  /**
   * for Target JDKs versions 1.2.x - 1.3.0 the Classic VM should be used for debugging
   */
  private static boolean shouldForceClassicVM(Sdk jdk) {
    if (SystemInfo.isMac) {
      return false;
    }
    if (jdk == null) return false;

    String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (version != null) {
      if (version.compareTo("1.4") >= 0) {
        return false;
      }
      if (version.startsWith("1.2") && SystemInfo.isWindows) {
        return true;
      }
      version += ".0";
      if (version.startsWith("1.3.0") && SystemInfo.isWindows) {
        return true;
      }
      if ((version.startsWith("1.3.1_07") || version.startsWith("1.3.1_08")) && SystemInfo.isWindows) {
        return false; // fixes bug for these JDKs that it cannot start with -classic option
      }
    }

    return DebuggerSettings.getInstance().FORCE_CLASSIC_VM;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean debuggerInServerMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity)
    throws ExecutionException {
    if (checkValidity) {
      checkTargetJPDAInstalled(parameters);
    }

    final boolean useSockets = transport == DebuggerSettings.SOCKET_TRANSPORT;

    String address = "";
    if (StringUtil.isEmptyOrSpaces(debugPort)) {
      try {
        address = DebuggerUtils.getInstance().findAvailableDebugAddress(useSockets);
      }
      catch (ExecutionException e) {
        if (checkValidity) {
          throw e;
        }
      }
    }
    else {
      address = debugPort;
    }

    final TransportServiceWrapper transportService = TransportServiceWrapper.getTransportService(useSockets);
    final String debugAddress = debuggerInServerMode && useSockets ? "127.0.0.1:" + address : address;
    String debuggeeRunProperties = "transport=" + transportService.transportId() + ",address=" + debugAddress;
    if (debuggerInServerMode) {
      debuggeeRunProperties += ",suspend=y,server=n";
    }
    else {
      debuggeeRunProperties += ",suspend=n,server=y";
    }

    if (hasWhitespace(debuggeeRunProperties)) {
      debuggeeRunProperties = "\"" + debuggeeRunProperties + "\"";
    }
    final String _debuggeeRunProperties = debuggeeRunProperties;

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void run() {
        JavaSdkUtil.addRtJar(parameters.getClassPath());

        final Sdk jdk = parameters.getJdk();
        final boolean forceClassicVM = shouldForceClassicVM(jdk);
        final boolean forceNoJIT = shouldForceNoJIT(jdk);
        final String debugKey = System.getProperty(DEBUG_KEY_NAME, "-Xdebug");
        final boolean needDebugKey = shouldAddXdebugKey(jdk) || !"-Xdebug".equals(debugKey) /*the key is non-standard*/;

        if (forceClassicVM || forceNoJIT || needDebugKey || !isJVMTIAvailable(jdk)) {
          parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "-Xrunjdwp:" + _debuggeeRunProperties);
        }
        else {
          // use newer JVMTI if available
          parameters.getVMParametersList().replaceOrPrepend("-Xrunjdwp:", "");
          parameters.getVMParametersList().replaceOrPrepend("-agentlib:jdwp=", "-agentlib:jdwp=" + _debuggeeRunProperties);
        }

        if (forceNoJIT) {
          parameters.getVMParametersList().replaceOrPrepend("-Djava.compiler=", "-Djava.compiler=NONE");
          parameters.getVMParametersList().replaceOrPrepend("-Xnoagent", "-Xnoagent");
        }

        if (needDebugKey) {
          parameters.getVMParametersList().replaceOrPrepend(debugKey, debugKey);
        }
        else {
          // deliberately skip outdated parameter because it can disable full-speed debugging for some jdk builds
          // see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6272174
          parameters.getVMParametersList().replaceOrPrepend("-Xdebug", "");
        }

        parameters.getVMParametersList().replaceOrPrepend("-classic", forceClassicVM ? "-classic" : "");
      }
    });

    return new RemoteConnection(useSockets, "127.0.0.1", address, debuggerInServerMode);
  }

  private static boolean shouldForceNoJIT(Sdk jdk) {
    if (DebuggerSettings.getInstance().DISABLE_JIT) {
      return true;
    }
    if (jdk != null) {
      final String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
      if (version != null && (version.startsWith("1.2") || version.startsWith("1.3"))) {
        return true;
      }
    }
    return false;
  }

  private static boolean shouldAddXdebugKey(Sdk jdk) {
    if (jdk == null) {
      return true; // conservative choice
    }
    if (DebuggerSettings.getInstance().DISABLE_JIT) {
      return true;
    }

    //if (ApplicationManager.getApplication().isUnitTestMode()) {
    // need this in unit tests to avoid false alarms when comparing actual output with expected output
    //return true;
    //}

    final String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    return version == null ||
           //version.startsWith("1.5") ||
           version.startsWith("1.4") ||
           version.startsWith("1.3") ||
           version.startsWith("1.2") ||
           version.startsWith("1.1") ||
           version.startsWith("1.0");
  }

  private static boolean isJVMTIAvailable(Sdk jdk) {
    if (jdk == null) {
      return false; // conservative choice
    }

    final String version = JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
    if (version == null) {
      return false;
    }
    return !(version.startsWith("1.4") ||
             version.startsWith("1.3") ||
             version.startsWith("1.2") ||
             version.startsWith("1.1") ||
             version.startsWith("1.0"));
  }

  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       GenericDebuggerRunnerSettings settings,
                                                       boolean checkValidity)
    throws ExecutionException {
    return createDebugParameters(parameters, settings.LOCAL, settings.getTransport(), settings.DEBUG_PORT, checkValidity);
  }

  private static class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerSession myDebuggerSession;

    @Override
    public DebuggerContextImpl getContext() {
      return myDebuggerSession == null ? DebuggerContextImpl.EMPTY_CONTEXT : myDebuggerSession.getContextManager().getContext();
    }

    @Override
    public void setState(final DebuggerContextImpl context, int state, int event, String description) {
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
    ProcessHandler processHandler = session.getProcess().getExecutionResult().getProcessHandler();

    synchronized (mySessions) {
      DebuggerSession removed = mySessions.remove(processHandler);
      LOG.assertTrue(removed != null);
      myDispatcher.getMulticaster().sessionRemoved(session);
    }
  }
}
