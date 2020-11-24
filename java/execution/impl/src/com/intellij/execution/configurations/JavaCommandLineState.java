// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration;
import com.intellij.execution.wsl.target.WslTargetEnvironmentFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine, TargetEnvironmentAwareRunProfileState, RemoteConnectionCreator {
  private static final Logger LOG = Logger.getInstance(JavaCommandLineState.class);
  private JavaParameters myParams;
  private TargetEnvironmentRequest myTargetEnvironmentRequest;
  private TargetedCommandLineBuilder myCommandLine;
  @Nullable private volatile RemoteConnection myRemoteConnection;
  @Nullable private TargetEnvironment.TargetPortBinding myDebuggerPortRequest;

  protected JavaCommandLineState(@NotNull ExecutionEnvironment environment) {
    super(environment);
  }

  @Override
  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myParams == null) {
      myParams = ReadAction.compute(this::createJavaParameters);
    }
    return myParams;
  }

  public void clear() {
    myParams = null;
  }

  @Override
  @NotNull
  protected OSProcessHandler startProcess() throws ExecutionException {
    return JavaCommandLineStateUtil.startProcess(createCommandLine(), ansiColoringEnabled());
  }

  protected boolean ansiColoringEnabled() {
    return true;
  }

  protected abstract JavaParameters createJavaParameters() throws ExecutionException;

  @Override
  public TargetEnvironmentFactory createCustomTargetEnvironmentFactory() {
    try {
      JavaParameters parameters = getJavaParameters();
      return checkCreateWslFactory(parameters);
    }
    catch (ExecutionException e) {
      // ignore
    }
    return null;
  }

  @Nullable
  private static WslTargetEnvironmentFactory checkCreateWslFactory(JavaParameters parameters) {
    String path;
    try {
      path = parameters.getJdkPath();
    }
    catch (CantRunException e) {
      return null;
    }
    Pair<String, @Nullable WSLDistribution> pathInWsl = WslDistributionManager.getInstance().parseWslPath(path);
    Sdk jdk = parameters.getJdk();
    if (jdk != null && pathInWsl != null && pathInWsl.second != null) {
      WslTargetEnvironmentConfiguration config = new WslTargetEnvironmentConfiguration(pathInWsl.second);
      JavaLanguageRuntimeConfiguration javaConfig = new JavaLanguageRuntimeConfiguration();
      javaConfig.setHomePath(pathInWsl.first);
      String jdkVersionString = jdk.getVersionString();
      if (jdkVersionString != null) {
        javaConfig.setJavaVersionString(jdkVersionString);
      }
      config.addLanguageRuntime(javaConfig);
      return new WslTargetEnvironmentFactory(config);
    }
    return null;
  }

  @Override
  public void prepareTargetEnvironmentRequest(
    @NotNull TargetEnvironmentRequest request,
    @Nullable TargetEnvironmentConfiguration configuration,
    @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException {
    prepareRemoteConnection(request, configuration);

    targetProgressIndicator.addSystemLine(ExecutionBundle.message("progress.text.prepare.target.requirements"));
    myTargetEnvironmentRequest = request;
    Ref<TargetedCommandLineBuilder> commandLineRef = new Ref<>();
    Ref<ExecutionException> exceptionRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        commandLineRef.set(createTargetedCommandLine(myTargetEnvironmentRequest, configuration));
      }
      catch (ExecutionException e) {
        exceptionRef.set(e);
      }
    });
    if(!exceptionRef.isNull()){
      throw exceptionRef.get();
    }
    myCommandLine = commandLineRef.get();

    TargetEnvironment.TargetPortBinding portRequest = myDebuggerPortRequest;
    if (portRequest != null) {
      Objects.requireNonNull(request).getTargetPortBindings().add(portRequest);
    }
  }

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment,
                                             @NotNull TargetProgressIndicator targetProgressIndicator) {
    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_SETUP_KEY))
      .provideEnvironment(environment, targetProgressIndicator);

    TargetEnvironment.TargetPortBinding portRequest = myDebuggerPortRequest;
    if (portRequest != null) {
      setRemoteConnectionPort(environment.getTargetPortBindings().get(portRequest));
    }
  }

  public void setRemoteConnectionPort(int port) {
    RemoteConnection c = myRemoteConnection;
    if (c != null) {
      c.setDebuggerHostName("localhost");
      c.setDebuggerAddress(String.valueOf(port));
    }
  }

  private synchronized void prepareRemoteConnection(
    @NotNull TargetEnvironmentRequest request,
    @Nullable TargetEnvironmentConfiguration configuration
  ) {
    myDebuggerPortRequest = null;
    final int remotePort;
    {
      Integer remotePort2 = requiredDebuggerTargetPort(request);
      if (remotePort2 == null) {
        myRemoteConnection = null;
        return;
      }
      remotePort = remotePort2;
    }

    try {
      final String remoteAddressForVmParams;

      final boolean java9plus = Optional.ofNullable(configuration)
        .map(TargetEnvironmentConfiguration::getRuntimes)
        .map(list -> list.findByType(JavaLanguageRuntimeConfiguration.class))
        .map(JavaLanguageRuntimeConfiguration::getJavaVersionString)
        .filter(StringUtil::isNotEmpty)
        .map(JavaSdkVersion::fromVersionString)
        .map(v -> v.isAtLeast(JavaSdkVersion.JDK_1_9))
        .orElse(false);

      if (java9plus) {
        // IDEA-225182 - hack: pass "host:port" to construct correct VM params, then adjust the connection
        remoteAddressForVmParams = "*:" + remotePort;
      }
      else {
        remoteAddressForVmParams = String.valueOf(remotePort);
      }

      RemoteConnection remoteConnection = new RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
        .suspend(true)
        .create(getJavaParameters());

      remoteConnection.setApplicationAddress(String.valueOf(remotePort));
      if (java9plus) {
        remoteConnection.setApplicationHostName("*");
      }

      myDebuggerPortRequest = new TargetEnvironment.TargetPortBinding(null, remotePort);

      myRemoteConnection = remoteConnection;
    }
    catch (ExecutionException e) {
      myRemoteConnection = null;
    }
  }

  @Nullable
  @Override
  public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    return myRemoteConnection;
  }

  @Override
  public boolean isPollConnection() {
    return true;
  }

  public synchronized @Nullable TargetEnvironmentRequest getTargetEnvironmentRequest() {
    return myTargetEnvironmentRequest;
  }

  public @Nullable Integer requiredDebuggerTargetPort(@NotNull TargetEnvironmentRequest request) {
    // TODO Checking for a specific target is a gap in the idea of API. This check was introduced because the Java debugger
    //  runs in the server mode for local targets and in the client mode for other targets. But why?
    //  Anyway, the server mode requires a remote TCP forwarding that can't always be acquired for the Docker target.
    //  Maybe replace this method with something like `if (!request.isLocalPortForwardingSupported())`?
    if (DefaultDebugExecutor.EXECUTOR_ID.equalsIgnoreCase(getEnvironment().getExecutor().getId()) &&
        !(request instanceof LocalTargetEnvironmentRequest)) {
      return 12345;
    }
    else {
      return null;
    }
  }

  @NotNull
  protected synchronized TargetedCommandLineBuilder getTargetedCommandLine() {
    if (myCommandLine != null) {
      // In a correct implementation that uses the new API this condition is always true.
      return myCommandLine;
    }

    if (Experiments.getInstance().isFeatureEnabled("run.targets") &&
        !(getEnvironment().getTargetEnvironmentFactory() instanceof LocalTargetEnvironmentFactory)) {
      LOG.error("Command line hasn't been built yet. " +
                "Probably you need to run environment#getPreparedTargetEnvironment first, " +
                "or it return the environment from the previous run session");
    }
    try {
      // force re-prepareTargetEnvironment in order to drop previous environment
      getEnvironment().prepareTargetEnvironment(this, TargetProgressIndicator.EMPTY);
      return myCommandLine;
    }
    catch (ExecutionException e) {
      LOG.error(e);
      throw new RuntimeException(e);
    }
  }

  @NotNull
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                 @Nullable TargetEnvironmentConfiguration configuration)
    throws ExecutionException {
    SimpleJavaParameters javaParameters = getJavaParameters();
    if (!javaParameters.isDynamicClasspath()) {
      javaParameters.setUseDynamicClasspath(getEnvironment().getProject());
    }
    return javaParameters.toCommandLine(request, configuration);
  }

  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    LocalTargetEnvironmentFactory factory = new LocalTargetEnvironmentFactory();
    boolean redirectErrorStream = Registry.is("run.processes.with.redirectedErrorStream", false);
    TargetEnvironmentRequest request = factory.createRequest();
    TargetedCommandLineBuilder targetedCommandLineBuilder = createTargetedCommandLine(request, factory.getTargetConfiguration());
    LocalTargetEnvironment environment = factory.prepareRemoteEnvironment(request, TargetProgressIndicator.EMPTY);
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_SETUP_KEY))
      .provideEnvironment(environment, TargetProgressIndicator.EMPTY);
    return environment
      .createGeneralCommandLine(targetedCommandLineBuilder.build())
      .withRedirectErrorStream(redirectErrorStream);
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}