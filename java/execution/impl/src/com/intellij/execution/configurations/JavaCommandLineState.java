// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.TargetDebuggerConnection;
import com.intellij.execution.TargetDebuggerConnectionUtil;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.eel.EelTargetEnvironmentRequest;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.wsl.WslPath;
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.eel.LocalEelApi;
import com.intellij.platform.eel.provider.EelProviderUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine, TargetEnvironmentAwareRunProfileState, RemoteConnectionCreator {
  private static final Logger LOG = Logger.getInstance(JavaCommandLineState.class);
  private JavaParameters myParams;
  private TargetEnvironmentRequest myTargetEnvironmentRequest;
  private TargetedCommandLineBuilder myCommandLine;
  @Nullable private volatile TargetDebuggerConnection myTargetDebuggerConnection;

  protected JavaCommandLineState(@NotNull ExecutionEnvironment environment) {
    super(environment);
  }

  @Override
  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myParams == null) {
      myParams = isReadActionRequired() ? ReadAction.compute(this::createJavaParameters) : createJavaParameters();
    }
    return myParams;
  }

  protected boolean isReadActionRequired() {
    return true;
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
  public TargetEnvironmentRequest createCustomTargetEnvironmentRequest() {
    try {
      JavaParameters parameters = getJavaParameters();
      var config = checkCreateNonLocalConfiguration(parameters.getJdk());
      return config == null ? null : new EelTargetEnvironmentRequest(config);
    }
    catch (ExecutionException e) {
      // ignore
    }
    return null;
  }

  @Nullable
  @ApiStatus.Internal
  public static EelTargetEnvironmentRequest.Configuration checkCreateNonLocalConfiguration(@Nullable Sdk jdk) {
    if (jdk == null) {
      return null;
    }

    VirtualFile virtualFile = jdk.getHomeDirectory();

    if (virtualFile == null) {
      return null;
    }

    var vitrualFilePath = virtualFile.toNioPath();
    var eel = EelProviderUtil.getEelApiBlocking(vitrualFilePath);

    if (!(eel instanceof LocalEelApi)) {
      var config = new EelTargetEnvironmentRequest.Configuration(eel);
      addJavaLangConfig(config, Objects.requireNonNull(eel.getMapper().getOriginalPath(vitrualFilePath)).toString(), jdk);
      return config;
    }
    return null;
  }

  @ApiStatus.Obsolete
  public static WslTargetEnvironmentConfiguration checkCreateWslConfiguration(@Nullable Sdk jdk) {
    if (jdk == null) {
      return null;
    }
    VirtualFile virtualFile = jdk.getHomeDirectory();
    if (virtualFile == null) {
      return null;
    }
    WslPath wslPath = WslPath.parseWindowsUncPath(virtualFile.getPath());
    if (wslPath != null) {
      WslTargetEnvironmentConfiguration config = new WslTargetEnvironmentConfiguration(wslPath.getDistribution());
      addJavaLangConfig(config, wslPath.getLinuxPath(), jdk);
      return config;
    }
    return null;
  }

  private static void addJavaLangConfig(TargetEnvironmentConfiguration config, String javaHomePath, Sdk jdk) {
    JavaLanguageRuntimeConfiguration javaConfig = new JavaLanguageRuntimeConfiguration();
    javaConfig.setHomePath(javaHomePath);
    String jdkVersionString = jdk.getVersionString();
    if (jdkVersionString != null) {
      javaConfig.setJavaVersionString(jdkVersionString);
    }
    config.addLanguageRuntime(javaConfig);
  }

  protected boolean shouldPrepareDebuggerConnection() {
    return true;
  }

  @Override
  public void prepareTargetEnvironmentRequest(
    @NotNull TargetEnvironmentRequest request,
    @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException {
    targetProgressIndicator.addSystemLine(ExecutionBundle.message("progress.text.prepare.target.requirements"));

    myTargetEnvironmentRequest = request;

    TargetDebuggerConnection targetDebuggerConnection =
      shouldPrepareDebuggerConnection() ? TargetDebuggerConnectionUtil.prepareDebuggerConnection(this, request) : null;
    myTargetDebuggerConnection = targetDebuggerConnection;

    myCommandLine = createTargetedCommandLine(myTargetEnvironmentRequest);

    if (targetDebuggerConnection != null) {
      Objects.requireNonNull(request).getTargetPortBindings().add(targetDebuggerConnection.getDebuggerPortRequest());
    }
  }

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment,
                                             @NotNull TargetProgressIndicator targetProgressIndicator) {
    TargetDebuggerConnection targetDebuggerConnection = myTargetDebuggerConnection;
    if (targetDebuggerConnection != null) {
      targetDebuggerConnection.resolveRemoteConnection(environment);
    }
  }

  @Nullable
  @Override
  public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    TargetDebuggerConnection targetDebuggerConnection = myTargetDebuggerConnection;
    if (targetDebuggerConnection != null) {
      return targetDebuggerConnection.getResolvedRemoteConnection();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isPollConnection() {
    return true;
  }

  public synchronized @Nullable TargetEnvironmentRequest getTargetEnvironmentRequest() {
    return myTargetEnvironmentRequest;
  }

  @NotNull
  protected synchronized TargetedCommandLineBuilder getTargetedCommandLine() {
    if (myCommandLine != null) {
      // In a correct implementation that uses the new API this condition is always true.
      return myCommandLine;
    }

    if (RunTargetsEnabled.get() &&
        !(getEnvironment().getTargetEnvironmentRequest() instanceof LocalTargetEnvironmentRequest)) {
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
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
    throws ExecutionException {
    SimpleJavaParameters javaParameters = getJavaParameters();
    if (!javaParameters.isDynamicClasspath()) {
      javaParameters.setUseDynamicClasspath(getEnvironment().getProject());
    }
    return javaParameters.toCommandLine(request);
  }

  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    boolean redirectErrorStream = Registry.is("run.processes.with.redirectedErrorStream", false);
    LocalTargetEnvironmentRequest request = new LocalTargetEnvironmentRequest();
    TargetedCommandLineBuilder targetedCommandLineBuilder = createTargetedCommandLine(request);
    LocalTargetEnvironment environment = request.prepareEnvironment(TargetProgressIndicator.EMPTY);
    return environment
      .createGeneralCommandLine(targetedCommandLineBuilder.build())
      .withRedirectErrorStream(redirectErrorStream);
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}