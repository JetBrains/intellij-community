// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution;

import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

@ApiStatus.Internal
public class TargetDebuggerConnectionUtil {
  private TargetDebuggerConnectionUtil() {}

  @Nullable
  private static Integer requiredDebuggerTargetPort(@NotNull JavaCommandLineState javaCommandLineState,
                                                    @NotNull TargetEnvironmentRequest request) {
    // TODO Checking for a specific target is a gap in the idea of API. This check was introduced because the Java debugger
    //  runs in the server mode for local targets and in the client mode for other targets. But why?
    //  Anyway, the server mode requires a remote TCP forwarding that can't always be acquired for the Docker target.
    //  Maybe replace this method with something like `if (!request.isLocalPortForwardingSupported())`?
    if (DefaultDebugExecutor.EXECUTOR_ID.equalsIgnoreCase(javaCommandLineState.getEnvironment().getExecutor().getId()) &&
        !(request instanceof LocalTargetEnvironmentRequest)) {
      return 12345;
    }
    else {
      return null;
    }
  }

  /**
   * Performs preliminary work to configure debugger connection parameters to
   * start the Java process with. The method adds the debugger connection
   * parameters to the provided {@link JavaCommandLineState}. Then it returns
   * {@link TargetDebuggerConnection} object that could be used later to
   * resolve the connection parameters from IDE side against created
   * {@link TargetEnvironment}.
   * <p>
   * Does nothing and returns {@code null} for
   * {@link LocalTargetEnvironmentRequest} or an executor other than
   * {@link DefaultDebugExecutor}.
   *
   * @param javaCommandLineState the command line state that is going to be
   *                             modified
   * @param request              the target environment request
   * @param configuration        the target environment configuration
   * @return the constructed {@link TargetDebuggerConnection} object for
   * further resolution of connection parameters from IDE side or {@code null}
   * in the case of inappropriate {@link Executor} or the local type of the
   * {@code request}.
   */
  @Nullable
  public static TargetDebuggerConnection prepareDebuggerConnection(@NotNull JavaCommandLineState javaCommandLineState,
                                                                   @NotNull TargetEnvironmentRequest request) {
    final int remotePort;
    JavaParameters javaParameters;
    try {
      javaParameters = javaCommandLineState.getJavaParameters();
    } catch (ExecutionException e){
      return null;
    }

    {
      Integer remotePort2 = requiredDebuggerTargetPort(javaCommandLineState, request);
      if (remotePort2 == null) {
        return null;
      }
      remotePort = remotePort2;
    }

    try {
      final String remoteAddressForVmParams;

      final boolean java9plus = Optional.ofNullable(request.getConfiguration())
        .map(TargetEnvironmentConfiguration::getRuntimes)
        .map(list -> list.findByType(JavaLanguageRuntimeConfiguration.class))
        .map(JavaLanguageRuntimeConfiguration::getJavaVersionString)
        .filter(StringUtil::isNotEmpty)
        .map(JavaSdkVersion::fromVersionString)
        .map(v -> v.isAtLeast(JavaSdkVersion.JDK_1_9))
        .orElse(false);

      if (java9plus) {
        // IDEA-225182 - hack: pass "host:port" to construct correct VM params, then adjust the connection
        // IDEA-265364 - enforce ipv4 here with explicit 0.0.0.0 address
        remoteAddressForVmParams = "0.0.0.0:" + remotePort;
      }
      else {
        remoteAddressForVmParams = String.valueOf(remotePort);
      }

      RemoteConnection remoteConnection = new RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
        .suspend(true)
        .create(javaParameters);

      remoteConnection.setApplicationAddress(String.valueOf(remotePort));
      if (java9plus) {
        remoteConnection.setApplicationHostName("*");
      }

      return new TargetDebuggerConnection(remoteConnection, new TargetEnvironment.TargetPortBinding(null, remotePort));
    }
    catch (ExecutionException e) {
      return null;
    }
  }
}
