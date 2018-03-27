/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.impl;

import com.intellij.debugger.*;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.settings.CaptureSettingsProvider;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.GetJPDADialog;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.tree.render.BatchEvaluator;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
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
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.util.EventDispatcher;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.org.objectweb.asm.MethodVisitor;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.Attributes;
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

  public DebuggerManagerImpl(Project project, StartupManager startupManager) {
    myProject = project;
    myBreakpointManager = new BreakpointManager(myProject, startupManager, this);
    if (!project.isDefault()) {
      project.getMessageBus().connect().subscribe(EditorColorsManager.TOPIC, new EditorColorsListener() {
        @Override
        public void globalSchemeChange(EditorColorsScheme scheme) {
          getBreakpointManager().updateBreakpointsUI();
        }
      });
    }
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
    DebuggerSession session = DebuggerSession.create(environment.getSessionName(), debugProcess, environment);
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

  @Override
  @NotNull
  public String getComponentName() {
    return "DebuggerManager";
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

  @Override
  public void unregisterPositionManagerFactory(final Function<DebugProcess, PositionManager> factory) {
    myCustomPositionManagerFactories.remove(factory);
  }

  /* Remoting */
  private static void checkTargetJPDAInstalled(JavaParameters parameters) throws ExecutionException {
    final Sdk jdk = parameters.getJdk();
    if (jdk == null) {
      throw new ExecutionException(DebuggerBundle.message("error.jdk.not.specified"));
    }
    final JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version == JavaSdkVersion.JDK_1_0 || version == JavaSdkVersion.JDK_1_1) {
      String versionString = jdk.getVersionString();
      throw new ExecutionException(DebuggerBundle.message("error.unsupported.jdk.version", versionString));
    }
    if (SystemInfo.isWindows && version == JavaSdkVersion.JDK_1_2) {
      final VirtualFile homeDirectory = jdk.getHomeDirectory();
      if (homeDirectory == null || !homeDirectory.isValid()) {
        String versionString = jdk.getVersionString();
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
    if (version == null || StringUtil.compareVersionNumbers(version, "1.4") >= 0) {
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
    return DebuggerSettings.getInstance().FORCE_CLASSIC_VM;
  }

  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean debuggerInServerMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity) throws ExecutionException {
    return createDebugParameters(parameters, debuggerInServerMode, transport, debugPort, checkValidity, true);
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public static RemoteConnection createDebugParameters(final JavaParameters parameters,
                                                       final boolean debuggerInServerMode,
                                                       int transport, final String debugPort,
                                                       boolean checkValidity,
                                                       boolean addAsyncDebuggerAgent)
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

    final String debugAddress = debuggerInServerMode && useSockets ? LOCALHOST_ADDRESS_FALLBACK + ":" + address : address;
    String debuggeeRunProperties =
      "transport=" + DebugProcessImpl.findConnector(useSockets, debuggerInServerMode).transport().name() + ",address=" + debugAddress;
    if (debuggerInServerMode) {
      debuggeeRunProperties += ",suspend=y,server=n";
    }
    else {
      debuggeeRunProperties += ",suspend=n,server=y";
    }

    if (StringUtil.containsWhitespaces(debuggeeRunProperties)) {
      debuggeeRunProperties = "\"" + debuggeeRunProperties + "\"";
    }
    final String _debuggeeRunProperties = debuggeeRunProperties;

    ApplicationManager.getApplication().runReadAction(() -> {
      JavaSdkUtil.addRtJar(parameters.getClassPath());

      if (addAsyncDebuggerAgent) {
        addDebuggerAgent(parameters);
      }

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
    });

    return new RemoteConnection(useSockets, LOCALHOST_ADDRESS_FALLBACK, address, debuggerInServerMode);
  }

  private static final String AGENT_FILE_NAME = "debugger-agent.jar";
  private static final String STORAGE_FILE_NAME = "debugger-agent-storage.jar";

  private static void addDebuggerAgent(JavaParameters parameters) {
    if (StackCapturingLineBreakpoint.isAgentEnabled()) {
      String prefix = "-javaagent:";
      ParametersList parametersList = parameters.getVMParametersList();
      if (parametersList.getParameters().stream().noneMatch(p -> p.startsWith(prefix) && p.contains(AGENT_FILE_NAME))) {
        Sdk jdk = parameters.getJdk();
        String version = jdk != null ? JdkUtil.getJdkMainAttribute(jdk, Attributes.Name.IMPLEMENTATION_VERSION) : null;
        if (version != null) {
          JavaSdkVersion sdkVersion = JavaSdkVersion.fromVersionString(version);
          if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_6)) {
            File classesRoot = new File(PathUtil.getJarPathForClass(DebuggerManagerImpl.class));
            File agentFile;
            if (classesRoot.isFile()) {
              agentFile = new File(classesRoot.getParentFile(), "rt/" + AGENT_FILE_NAME);
            }
            else {
              agentFile = new File(classesRoot.getParentFile().getParentFile(), "/artifacts/debugger_agent/" + AGENT_FILE_NAME);
            }
            if (agentFile.exists()) {
              String agentPath = handleSpacesInPath(agentFile.getAbsolutePath());
              if (agentPath != null) {
                parametersList.add(prefix + agentPath + "=" + generateAgentSettings());
              }
            }
            else {
              LOG.warn("Capture agent not found: " + agentFile);
            }
          }
          else {
            LOG.warn("Capture agent is not supported for jre " + version);
          }
        }
      }
    }
  }

  @Nullable
  private static String handleSpacesInPath(String agentPath) {
    if (agentPath.contains(" ")) {
      File targetDir = new File(PathManager.getSystemPath(), "captureAgent");
      if (targetDir.getAbsolutePath().contains(" ")) {
        try {
          targetDir = FileUtil.createTempDirectory("capture", "jars");
          if (targetDir.getAbsolutePath().contains(" ")) {
            LOG.info("Capture agent was not used since the agent path contained spaces: " + agentPath);
            return null;
          }
        }
        catch (IOException e) {
          LOG.info(e);
          return null;
        }
      }

      try {
        targetDir.mkdirs();
        Path source = Paths.get(agentPath);
        Path target = targetDir.toPath().resolve(AGENT_FILE_NAME);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(source.getParent().resolve(STORAGE_FILE_NAME), targetDir.toPath().resolve(STORAGE_FILE_NAME),
                   StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
      }
      catch (IOException e) {
        LOG.info(e);
        return null;
      }
    }
    return agentPath;
  }

  private static String generateAgentSettings() {
    Properties properties = new Properties();
    properties.setProperty("asm-lib", PathUtil.getJarPathForClass(MethodVisitor.class));
    if (Registry.is("debugger.capture.points.agent.debug")) {
      properties.setProperty("debug", "true");
    }
    int idx = 0;
    for (CaptureSettingsProvider.AgentPoint point : CaptureSettingsProvider.getPoints()) {
      properties.setProperty((point.isCapture() ? "capture" : "insert") + idx++,
                             point.myClassName + CaptureSettingsProvider.AgentPoint.SEPARATOR +
                             point.myMethodName + CaptureSettingsProvider.AgentPoint.SEPARATOR +
                             point.myKey.asString());
    }
    try {
      File file = FileUtil.createTempFile("capture", ".props");
      try (FileOutputStream out = new FileOutputStream(file)) {
        properties.store(out, null);
        return file.getAbsolutePath();
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return null;
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
