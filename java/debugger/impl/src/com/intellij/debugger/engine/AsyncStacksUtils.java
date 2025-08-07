// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.debugger.settings.CaptureSettingsProvider;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.testFramework.TestDebuggerAgentArtifactsProvider;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.platform.eel.EelDescriptor;
import com.intellij.platform.eel.annotations.NativePath;
import com.intellij.platform.eel.provider.EelProviderUtil;
import com.intellij.platform.eel.provider.LocalEelDescriptor;
import com.intellij.platform.eel.provider.utils.EelPathUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.sun.jdi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.BuildDependenciesJps;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.intellij.platform.eel.provider.EelNioBridgeServiceKt.asEelPath;

public final class AsyncStacksUtils {
  private static final Logger LOG = Logger.getInstance(AsyncStacksUtils.class);
  // TODO: obtain CaptureStorage fqn from the class somehow
  public static final String CAPTURE_STORAGE_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureStorage";
  public static final String CAPTURE_AGENT_CLASS_NAME = "com.intellij.rt.debugger.agent.CaptureAgent";
  private static final String AGENT_JAR_NAME = "debugger-agent.jar";
  private static final Key<Boolean> ASYNC_STACKS_ENABLED = Key.create("ASYNC_STACKS_ENABLED");

  @ApiStatus.Internal
  public static Boolean isAsyncStacksEnabled(XDebugSessionImpl session) {
    return ASYNC_STACKS_ENABLED.get(session.getSessionData(), true);
  }

  @ApiStatus.Internal
  public static void setAsyncStacksEnabled(XDebugSessionImpl session, Boolean state) {
    ASYNC_STACKS_ENABLED.set(session.getSessionData(), state);
  }

  public static boolean isAgentEnabled() {
    return DebuggerSettings.getInstance().INSTRUMENTING_AGENT;
  }

  public static boolean isSuspendHelperEnabled() {
    return isAgentEnabled() && Registry.is("debugger.run.suspend.helper");
  }

  /**
   * Returns async stack trace captured by the debugger-agent for the thread corresponding to the given frame or null.
   *
   * If `debugger.async.stack.trace.for.all.threads` is true, returns captured async stack trace for any thread;
   * otherwise only returns captured async stack trace for the current thread and null for other threads.
   */
  public static @Nullable List<@Nullable StackFrameItem> getAgentRelatedStack(@NotNull StackFrameProxyImpl frame, @NotNull SuspendContextImpl suspendContext) {
    if (!isAgentEnabled()) {
      return null;
    }
    try {
      Method method = DebuggerUtilsEx.getMethod(frame.location());
      // TODO: use com.intellij.rt.debugger.agent.CaptureStorage.GENERATED_INSERT_METHOD_POSTFIX
      if (method != null && method.name().endsWith("$$$capture")) {
        return getCapturedStackForThread(new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy()), frame.threadProxy().getThreadReference());
      }
    }
    catch (EvaluateException e) {
      ObjectReference targetException = e.getExceptionFromTargetVM();
      if (e.getCause() instanceof IncompatibleThreadStateException) {
        LOG.warn(e);
      }
      else if (targetException != null && DebuggerUtils.instanceOf(targetException.type(), "java.lang.StackOverflowError")) {
        LOG.warn(e);
      }
      else {
        LOG.error(e);
      }
    }
    return null;
  }

  private static final Key<Pair<ClassType, Method>> CAPTURE_STORAGE_METHOD = Key.create("CAPTURE_STORAGE_METHOD");
  private static final Pair<ClassType, Method> NO_CAPTURE_AGENT = Pair.empty();

  private static List<StackFrameItem> getCapturedStackForThread(EvaluationContextImpl evalContext, ThreadReference threadReference)
    throws EvaluateException {
    EvaluationContextImpl evaluationContext = evalContext.withAutoLoadClasses(false);

    DebugProcessImpl process = evaluationContext.getDebugProcess();
    VirtualMachineProxyImpl virtualMachineProxy = evalContext.getVirtualMachineProxy();
    Pair<ClassType, Method> methodPair = virtualMachineProxy.getUserData(CAPTURE_STORAGE_METHOD);

    if (methodPair == null) {
      try {
        ClassType captureClass = (ClassType)process.findClass(evaluationContext, CAPTURE_STORAGE_CLASS_NAME, null);
        if (captureClass == null) {
          methodPair = NO_CAPTURE_AGENT;
          LOG.debug("Error loading debug agent", "agent class not found");
        }
        else {
          methodPair = Pair.create(captureClass, DebuggerUtils.findMethod(captureClass, "getCapturedStackForThread", null));
        }
      }
      catch (EvaluateException e) {
        methodPair = NO_CAPTURE_AGENT;
        LOG.debug("Error loading debug agent", e);
      }
      virtualMachineProxy.putUserData(CAPTURE_STORAGE_METHOD, methodPair);
    }

    if (methodPair == NO_CAPTURE_AGENT) {
      return null;
    }

    Pair<ClassType, Method> finalMethodPair = methodPair;
    List<Value> args = Arrays.asList(evaluationContext.getVirtualMachineProxy().mirrorOf(getMaxStackLength()), threadReference);
    String value = DebuggerUtils.getInstance().processCollectibleValue(
      () -> process.invokeMethod(evaluationContext, finalMethodPair.first, finalMethodPair.second,
                                 args, ObjectReference.INVOKE_SINGLE_THREADED, true),
      result -> result instanceof StringReference ? ((StringReference)result).value() : null,
      evaluationContext);
    if (value != null) {
      return parseAgentAsyncStackTrace(value, virtualMachineProxy);
    }
    return null;
  }

  @ApiStatus.Internal
  public static List<StackFrameItem> parseAgentAsyncStackTrace(String value, VirtualMachineProxyImpl vm) {
    List<StackFrameItem> res = new ArrayList<>();
    try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value.getBytes(StandardCharsets.ISO_8859_1)))) {
      while (dis.available() > 0) {
        StackFrameItem item = null;
        if (dis.readBoolean()) {
          String className = dis.readUTF();
          String methodName = dis.readUTF();
          int line = dis.readInt();
          Location location = DebuggerUtilsEx.findOrCreateLocation(vm.getVirtualMachine(), className, methodName, line);
          item = new StackFrameItem(location, null);
        }
        res.add(item);
      }
      return res;
    }
    catch (Exception e) {
      DebuggerUtilsImpl.logError(e);
    }
    return null;
  }

  public static void setupAgent(DebugProcessImpl process) {
    if (!isAgentEnabled()) {
      return;
    }

    // set debug mode
    if (Registry.is("debugger.capture.points.agent.debug")) {
      enableAgentDebug(process);
    }

    // add points
    if (DebuggerUtilsImpl.isRemote(process)) {
      Properties properties = CaptureSettingsProvider.getPointsProperties(process.getProject());
      if (!properties.isEmpty()) {
        process.addDebugProcessListener(new DebugProcessAdapterImpl() {
          @Override
          public void paused(SuspendContextImpl suspendContext) {
            if (process.isEvaluationPossible()) { // evaluation is possible
              try {
                StackCapturingLineBreakpoint.deleteAll(process);

                try {
                  addAgentCapturePoints(new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy()), properties);
                  process.removeDebugProcessListener(this);
                }
                finally {
                  process.onHotSwapFinished();
                  StackCapturingLineBreakpoint.createAll(process);
                }
              }
              catch (Exception e) {
                LOG.debug(e);
              }
            }
          }
        });
      }
    }
  }

  private static void enableAgentDebug(DebugProcessImpl process) {
    final RequestManagerImpl requestsManager = process.getRequestsManager();
    ClassPrepareRequestor requestor = new ClassPrepareRequestor() {
      @Override
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        try {
          requestsManager.deleteRequest(this);
          ((ClassType)referenceType).setValue(DebuggerUtils.findField(referenceType, "DEBUG"), referenceType.virtualMachine().mirrorOf(true));
        }
        catch (Exception e) {
          LOG.warn("Error setting agent debug mode", e);
        }
      }
    };
    requestsManager.callbackOnPrepareClasses(requestor, CAPTURE_STORAGE_CLASS_NAME);
    try {
      ClassType captureClass = (ClassType)process.findClass(null, CAPTURE_STORAGE_CLASS_NAME, null);
      if (captureClass != null) {
        requestor.processClassPrepare(process, captureClass);
      }
    }
    catch (Exception e) {
      LOG.warn("Error setting agent debug mode", e);
    }
  }

  public static void addAgentCapturePoints(EvaluationContextImpl evalContext, Properties properties) {
    EvaluationContextImpl evaluationContext = evalContext.withAutoLoadClasses(false);
    DebugProcessImpl process = evaluationContext.getDebugProcess();
    try {
      ClassType captureClass = (ClassType)process.findClass(evaluationContext, CAPTURE_AGENT_CLASS_NAME, null);
      if (captureClass == null) {
        LOG.debug("Error loading debug agent", "agent class not found");
      }
      else {
        Method method = DebuggerUtils.findMethod(captureClass, "addCapturePoints", null);
        if (method != null) {
          StringWriter writer = new StringWriter();
          try {
            properties.store(writer, null);
            var stringArgs = DebuggerUtilsEx.mirrorOfString(writer.toString(), evalContext);
            List<StringReference> args = Collections.singletonList(stringArgs);
            try {
              process.invokeMethod(evaluationContext, captureClass, method, args, ObjectReference.INVOKE_SINGLE_THREADED, true);
            }
            finally {
              DebuggerUtilsEx.enableCollection(stringArgs);
            }
          }
          catch (Exception e) {
            DebuggerUtilsImpl.logError(e);
          }
        }
      }
    }
    catch (EvaluateException e) {
      LOG.debug("Error loading debug agent", e);
    }
  }

  public static <T> void putProcessUserData(@NotNull Key<T> key, @Nullable T value, DebugProcessImpl debugProcess) {
    debugProcess.putUserData(key, value);
    debugProcess.addDebugProcessListener(new DebugProcessListener() {
      @Override
      public void processDetached(@NotNull DebugProcess process, boolean closedByUser) {
        process.putUserData(key, null);
      }
    });
  }

  public static int getMaxStackLength() {
    return Registry.intValue("debugger.async.stacks.max.depth", 500);
  }

  public static void addDebuggerAgent(JavaParameters parameters, @Nullable Project project, boolean checkJdkVersion) {
    addDebuggerAgent(parameters, project, checkJdkVersion, null);
  }

  public static void addDebuggerAgent(JavaParameters parameters,
                                      @Nullable Project project,
                                      boolean checkJdkVersion,
                                      @Nullable Disposable disposable) {
    if (isAgentEnabled()) {
      String prefix = "-javaagent:";
      ParametersList parametersList = parameters.getVMParametersList();
      if (!ContainerUtil.exists(parametersList.getParameters(), p -> p.startsWith(prefix) && p.contains(AGENT_JAR_NAME))) {
        Sdk jdk = parameters.getJdk();
        if (checkJdkVersion && jdk == null) {
          return;
        }
        JavaSdkVersion sdkVersion = jdk != null ? JavaSdk.getInstance().getVersion(jdk) : null;
        if (checkJdkVersion && (sdkVersion == null || !sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_7))) {
          LOG.warn("Capture agent is not supported for JRE " + sdkVersion);
          return;
        }

        extendParametersForAgent(project, disposable, parametersList, prefix);
      }
    }
  }

  private static void extendParametersForAgent(@Nullable Project project,
                                               @Nullable Disposable disposable,
                                               @NotNull ParametersList parametersList,
                                               @NotNull String prefix) {
    String agentPath = getAgentArtifactPath(project, disposable);
    if (agentPath != null) {
      try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307303, EA-835503")) {
        parametersList.prepend(prefix + agentPath + generateAgentSettings(project));
      }
      if (Registry.is("debugger.async.stacks.coroutines", false)) {
        parametersList.addProperty("kotlinx.coroutines.debug.enable.creation.stack.trace", "false");
        parametersList.addProperty("debugger.agent.enable.coroutines", "true");
        if (Registry.is("debugger.async.stacks.flows", false)) {
          parametersList.addProperty("kotlinx.coroutines.debug.enable.flows.stack.trace", "true");
        }
        if (Registry.is("debugger.async.stacks.state.flows", false)) {
          parametersList.addProperty("kotlinx.coroutines.debug.enable.mutable.state.flows.stack.trace", "true");
        }
      }
      if (!Registry.is("debugger.async.stack.trace.for.exceptions.printing", false)) {
        parametersList.addProperty("debugger.agent.support.throwable", "false");
      }
      if (Registry.is("debugger.async.stack.trace.for.all.threads")) {
        parametersList.addProperty("debugger.async.stack.trace.for.all.threads", "true");
      }
    }
    else {
      LOG.error("Capture agent not found: " + agentPath);
    }
  }

  /**
   * returns true if Bazel-test specific env variables are set
   */
  private static Boolean isBazelTestRun() {
    return System.getenv("TEST_SRCDIR") != null && System.getenv("TEST_UNDECLARED_OUTPUTS_DIR") != null;
  }

  @NativePath
  private static @Nullable String getAgentArtifactPath(@Nullable Project project, @Nullable Disposable disposable) {
    String relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation();
    Path classesRoot = Path.of(PathUtil.getJarPathForClass(DebuggerManagerImpl.class));
    // isDirectory(classesRoot) is used instead of `PluginManagerCore.isRunningFromSources()`
    // because we want to use installer's layout when running "IDEA (dev build)" run configuration
    // where the layout is quite the same as in installers.
    // but `PluginManagerCore.isRunningFromSources()` still returns `true` in this case
    if (Files.isDirectory(classesRoot) || (relevantJarsRoot != null && classesRoot.startsWith(relevantJarsRoot))) {
      return getArtifactPathForDownloadedAgent(project, disposable);
    }
    else {
      return getArtifactPathForBundledAgent(classesRoot, project, disposable);
    }
  }

  @NativePath
  private static @NotNull String getArtifactPathForDownloadedAgent(@Nullable Project project, @Nullable Disposable disposable) {
    // Code runs from IDEA run configuration (code from .class file in out/ directory)
    try {
      Path agentArtifactPath = createTemporaryAgentPath(project, disposable);

      Path downloadedAgent;
      // In jps runs we resolve the debugger agent via the standard BuildDependenciesJps path below.
      // However, when tests are executed under Bazel, they run inside a hermetic sandbox. In this environment:
      // - Network/downloads and access to arbitrary files are restricted.
      // - All runtime inputs must come from explicit Bazel-declared dependencies (runfiles).
      // Therefore, in Bazel unit-test mode we must obtain the agent JAR from the test classpath via a test-only
      // service. The TestDebuggerAgentArtifactsProvider is implemented in test sources and knows how to locate
      // the agent artifact provided by Bazel via runfiles, so we use ServiceLoader to find that provider
      // on the tests classpath and resolve the agent from there. Outside Bazel (regular production/dev runs), we
      // keep using the standard resolution path that downloads the dependency if needed.
      if (PluginManagerCore.isUnitTestMode && isBazelTestRun()) {
        ServiceLoader<TestDebuggerAgentArtifactsProvider> providerClasses = ServiceLoader.load(TestDebuggerAgentArtifactsProvider.class);
        var iterator = providerClasses.iterator();
        if (!iterator.hasNext()) {
          throw new IllegalStateException("TestDebuggerAgentArtifactsProvider service provider not found");
        }
        TestDebuggerAgentArtifactsProvider provider = iterator.next();
        if (iterator.hasNext()) {
          throw new IllegalStateException("more than one TestDebuggerAgentArtifactsProvider service providers found. Only one is expected");
        }

        downloadedAgent = provider.getDebuggerAgentJar();
      } else {
        Path communityRoot = Path.of(PathManager.getCommunityHomePath());
        Path iml = BuildDependenciesJps.getProjectModule(communityRoot, "intellij.java.debugger.agent.holder");
        downloadedAgent = BuildDependenciesJps.INSTANCE.getModuleLibrarySingleRootSync(
          iml,
          "debugger-agent",
          "https://cache-redirector.jetbrains.com/intellij-dependencies",
          new BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath())));
      }

      // The agent file must have a fixed name (AGENT_JAR_NAME) which is mentioned in MANIFEST.MF inside.
      // The copy operation is used as the rename operation.
      // toRealPath is required because EEL does not support copying of symbolic links
      Files.copy(downloadedAgent.toRealPath(), agentArtifactPath);

      return asEelPath(agentArtifactPath).toString();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NativePath
  private static @Nullable String getArtifactPathForBundledAgent(
    @NotNull Path classesRoot,
    @Nullable Project project,
    @Nullable Disposable disposable
  ) {
    Path bundledAgentPath = classesRoot.resolveSibling("rt").resolve(AGENT_JAR_NAME);
    if (!Files.exists(bundledAgentPath)) {
      return null;
    }
    EelDescriptor projectEelDescriptor = project == null ? null : EelProviderUtil.getEelDescriptor(project);
    if (project == null || LocalEelDescriptor.INSTANCE.equals(projectEelDescriptor)) {
      return JavaExecutionUtil.handleSpacesInAgentPath(
        bundledAgentPath.toAbsolutePath().toString(),
        "captureAgent",
        null,
        f -> f.getName().startsWith("debugger-agent")
      );
    }
    Path temporaryAgentPath = createTemporaryAgentPath(project, disposable);
    try {
      // toRealPath is required because EEL does not support copying of symbolic links
      EelPathUtils.transferLocalContentToRemote(
        bundledAgentPath.toRealPath(),
        new EelPathUtils.TransferTarget.Explicit(temporaryAgentPath)
      );
    } catch (IOException e) {
      LOG.error(String.format("Unable to copy the java-debugger agent file from %s to %s", bundledAgentPath, temporaryAgentPath), e);
      return null;
    }
    return asEelPath(temporaryAgentPath).toString();
  }

  private static @NotNull Path createTemporaryAgentPath(@Nullable Project project, @Nullable Disposable disposable) {
    // The agent file must have a fixed name (AGENT_JAR_NAME) which is mentioned in MANIFEST.MF inside
    Path debuggerAgentDir = EelPathUtils.createTemporaryDirectory(project, "debugger-agent", "", disposable == null);
    if (disposable != null) {
      Disposer.register(disposable, () -> {
        try {
          FileUtilRt.deleteRecursively(debuggerAgentDir);
        }
        catch (IOException ignored) {
        }
      });
    }
    return debuggerAgentDir.resolve(AGENT_JAR_NAME);
  }

  private static String generateAgentSettings(@Nullable Project project) {
    Properties properties = CaptureSettingsProvider.getPointsProperties(project);
    if (isSuspendHelperEnabled()) {
      properties.setProperty("suspendHelper", "true");
    }
    if (!properties.isEmpty()) {
      try {
        Path path = EelPathUtils.createTemporaryFile(project, "capture", ".props", true);
        try (OutputStream out = Files.newOutputStream(path)) {
          properties.store(out, null);
          return "=" + EelPathUtils.getUriLocalToEel(path).toASCIIString();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return "";
  }
}
