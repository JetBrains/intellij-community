package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.NameMapper;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.apiAdapters.TransportServiceWrapper;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.GetJPDADialog;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ex.PathUtilEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.Attributes;

public class DebuggerManagerImpl extends DebuggerManagerEx {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerManagerImpl");
  private final Project myProject;
  private final HashMap<ProcessHandler, DebuggerSession> mySessions = new HashMap<ProcessHandler, DebuggerSession>();
  private BreakpointManager myBreakpointManager;
  private List<NameMapper> myNameMappers = new CopyOnWriteArrayList<NameMapper>();
  private List<PositionManager> myCustomPositionManagers = new ArrayList<PositionManager>();
  
  private final EventDispatcher<DebuggerManagerListener> myDispatcher = EventDispatcher.create(DebuggerManagerListener.class);
  private final MyDebuggerStateManager myDebuggerStateManager = new MyDebuggerStateManager();

  private DebuggerContextListener mySessionListener = new DebuggerContextListener() {
    public void changeEvent(DebuggerContextImpl newContext, int event) {

      final DebuggerSession session = newContext.getDebuggerSession();
      if (event == DebuggerSession.EVENT_PAUSE && myDebuggerStateManager.myDebuggerSession != session) {
        // if paused in non-active session; switch current session
        myDebuggerStateManager.setState(newContext, session.getState(), event, null);
        return;
      }

      if(myDebuggerStateManager.myDebuggerSession == session) {
        myDebuggerStateManager.fireStateChanged(newContext, event);
      }

      if(event == DebuggerSession.EVENT_DISPOSE) {
        dispose(session);
        if(myDebuggerStateManager.myDebuggerSession == session) {
          myDebuggerStateManager.setState(DebuggerContextImpl.EMPTY_CONTEXT, DebuggerSession.STATE_DISPOSED, DebuggerSession.EVENT_DISPOSE, null);
        }
      }
    }
  };
  private static final @NonNls String DEBUG_KEY_NAME = "idea.xdebug.key";
  private EditorColorsListener myColorsListener;

  public void addClassNameMapper(final NameMapper mapper) {
    myNameMappers.add(mapper);
  }

  public void removeClassNameMapper(final NameMapper mapper) {
    myNameMappers.remove(mapper);
  }

  public String getVMClassQualifiedName(@NotNull final PsiClass aClass) {
    for (NameMapper nameMapper : myNameMappers) {
      final String qName = nameMapper.getQualifiedName(aClass);
      if (qName != null) {
        return qName;
      }
    }
    return aClass.getQualifiedName();
  }

  public void addDebuggerManagerListener(DebuggerManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeDebuggerManagerListener(DebuggerManagerListener listener) {
    myDispatcher.removeListener(listener);
  }

  public DebuggerManagerImpl(Project project, StartupManager startupManager, EditorColorsManager colorsManager) {
    myProject = project;
    myBreakpointManager = new BreakpointManager(myProject, startupManager, this);
    myColorsListener = new EditorColorsListener() {
      public void globalSchemeChange(EditorColorsScheme scheme) {
        getBreakpointManager().updateBreakpointsUI();
      }
    };
    colorsManager.addEditorColorsListener(myColorsListener);
  }

  public DebuggerSession getSession(DebugProcess process) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (final DebuggerSession debuggerSession : getSessions()) {
      if (process == debuggerSession.getProcess()) return debuggerSession;
    }
    return null;
  }

  public Collection<DebuggerSession> getSessions() {
    return mySessions.values();
  }

  public void disposeComponent() {
    EditorColorsManager.getInstance().removeEditorColorsListener(myColorsListener);
  }

  public void initComponent() {
  }

  public void projectClosed() {
    myBreakpointManager.dispose();
  }

  public void projectOpened() {
    myBreakpointManager.init();
  }

  public void readExternal(Element element) throws InvalidDataException {
    myBreakpointManager.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myBreakpointManager.writeExternal(element);
  }


  public DebuggerSession attachVirtualMachine(String sessionName,
                                              RunProfileState state,
                                              RemoteConnection remoteConnection,
                                              boolean pollConnection
  ) throws ExecutionException {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    final DebugProcessEvents debugProcess = new DebugProcessEvents(myProject);
    debugProcess.addDebugProcessListener(new DebugProcessAdapter() {
      public void processAttached(final DebugProcess process) {
        process.removeDebugProcessListener(this);
        for (PositionManager positionManager : myCustomPositionManagers) {
          process.appendPositionManager(positionManager);
        }
      }
      public void processDetached(final DebugProcess process, final boolean closedByUser) {
        debugProcess.removeDebugProcessListener(this);
      }
      public void attachException(final RunProfileState state, final ExecutionException exception, final RemoteConnection remoteConnection) {
        debugProcess.removeDebugProcessListener(this);
      }
    });
    DebuggerSession session = new DebuggerSession(sessionName, debugProcess);

    final ExecutionResult executionResult = session.attach(state, remoteConnection, pollConnection);
    if (executionResult == null) {
      return null;
    }
    session.getContextManager().addListener(mySessionListener);
    getContextManager().setState(DebuggerContextUtil.createDebuggerContext(session, session.getContextManager().getContext().getSuspendContext()), session.getState(), DebuggerSession.EVENT_CONTEXT, null);

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


  public DebugProcessImpl getDebugProcess(final ProcessHandler processHandler) {
    synchronized (mySessions) {
      DebuggerSession session = mySessions.get(processHandler);
      return session != null ? session.getProcess() : null;
    }
  }

  public void addDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.addDebugProcessListener(listener);
    }
    else {
      processHandler.addProcessListener(new ProcessAdapter() {
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

  public void removeDebugProcessListener(final ProcessHandler processHandler, final DebugProcessListener listener) {
    DebugProcessImpl debugProcess = getDebugProcess(processHandler);
    if (debugProcess != null) {
      debugProcess.removeDebugProcessListener(listener);
    }
    else {
      processHandler.addProcessListener(new ProcessAdapter() {
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

  public boolean isDebuggerManagerThread() {
    return DebuggerManagerThreadImpl.isManagerThread();
  }

  @NotNull
  public String getComponentName() {
    return "DebuggerManager";
  }

  public BreakpointManager getBreakpointManager() {
    return myBreakpointManager;
  }

  public DebuggerContextImpl getContext() { return getContextManager().getContext(); }

  public DebuggerStateManager getContextManager() { return myDebuggerStateManager;}

  public void registerPositionManager(final PositionManager positionManager) {
    myCustomPositionManagers.add(positionManager);
  }

  public void unregisterPositionManager(final PositionManager positionManager) {
    myCustomPositionManagers.remove(positionManager);
  }

  static private boolean hasWhitespace(String string) {
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
    final ProjectJdk jdk = parameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(DebuggerBundle.message("error.jdk.not.specified"));
    }
    final String versionString = jdk.getVersionString();
    if (versionString.indexOf("1.0") > -1 || versionString.indexOf("1.1") > -1) {
      throw new ExecutionException(DebuggerBundle.message("error.unsupported.jdk.version", versionString));
    }
    if (SystemInfo.isWindows && versionString.indexOf("1.2") > -1) {
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
  private static boolean shouldForceClassicVM(ProjectJdk jdk) {
    if (SystemInfo.isMac) {
      return false;
    }
    if (jdk == null) return false;

    String version = PathUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
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

    String address  = "";
    if (debugPort == null || "".equals(debugPort)) {
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

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void run() {
        PathUtilEx.addRtJar(parameters.getClassPath());
        boolean classicVM = shouldForceClassicVM(parameters.getJdk());
        parameters.getVMParametersList().replaceOrPrepend("-classic", classicVM ? "-classic" : "");

        final String debugKey = System.getProperty(DEBUG_KEY_NAME, "-Xdebug");
        parameters.getVMParametersList().replaceOrAppend(debugKey, debugKey);

        if (shouldForceNoJIT(parameters.getJdk())) {
          parameters.getVMParametersList().replaceOrAppend("-Xnoagent", "-Xnoagent");
          parameters.getVMParametersList().replaceOrAppend("-Djava.compiler=", "-Djava.compiler=NONE");
        }
      }
    });

    final TransportServiceWrapper transportService = TransportServiceWrapper.getTransportService(useSockets);
    final String debugAddress = (debuggerInServerMode && useSockets)? ("127.0.0.1:" + address) : address;
    String debuggeeRunProperties = "transport=" + transportService.transportId() + ",address=" + debugAddress;
    if(debuggerInServerMode) {
      debuggeeRunProperties += ",suspend=y,server=n";
    }
    else {
      debuggeeRunProperties += ",suspend=n,server=y";
    }

    if (hasWhitespace(debuggeeRunProperties)) {
      debuggeeRunProperties = "\"" + debuggeeRunProperties + "\"";
    }
    parameters.getVMParametersList().replaceOrAppend("-Xrunjdwp:", "-Xrunjdwp:" + debuggeeRunProperties);

    return new RemoteConnection(useSockets, "127.0.0.1", address, debuggerInServerMode);
  }

  private static boolean shouldForceNoJIT(ProjectJdk jdk) {
    if (DebuggerSettings.getInstance().DISABLE_JIT) {
      return true;
    }
    if (jdk != null) {
      final String version = PathUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION);
      if (version != null && (version.startsWith("1.2") || version.startsWith("1.3"))) {
        return true;
      }
    }
    return false;
  }

  public static RemoteConnection createDebugParameters(final JavaParameters parameters, GenericDebuggerRunnerSettings settings, boolean checkValidity)
    throws ExecutionException {
    return createDebugParameters(parameters, settings.LOCAL, settings.getTransport(), settings.DEBUG_PORT, checkValidity);
  }

  private static class MyDebuggerStateManager extends DebuggerStateManager {
    private DebuggerSession myDebuggerSession;

    public DebuggerContextImpl getContext() {
      return myDebuggerSession == null ? DebuggerContextImpl.EMPTY_CONTEXT : myDebuggerSession.getContextManager().getContext();
    }

    public void setState(final DebuggerContextImpl context, int state, int event, String description) {
      LOG.assertTrue(SwingUtilities.isEventDispatchThread());
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