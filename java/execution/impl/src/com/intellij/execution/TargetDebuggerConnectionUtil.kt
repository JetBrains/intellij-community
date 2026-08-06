// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.target.TargetEnvironment.TargetPortBinding
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.projectRoots.JavaSdkVersion

internal object TargetDebuggerConnectionUtil {

  private fun requiredDebuggerTargetPort(javaCommandLineState: JavaCommandLineState, request: TargetEnvironmentRequest): Int? {
    // TODO Checking for a specific target is a gap in the idea of API. This check was introduced because the Java debugger
    //  runs in the server mode for local targets and in the client mode for other targets. But why?
    //  Anyway, the server mode requires a remote TCP forwarding that can't always be acquired for the Docker target.
    //  Maybe replace this method with something like `if (!request.isLocalPortForwardingSupported())`?
    return if (
      DefaultDebugExecutor.EXECUTOR_ID.equals(javaCommandLineState.environment.executor.getId(), ignoreCase = true)
      && request !is LocalTargetEnvironmentRequest
    ) {
      12345
    }
    else {
      null
    }
  }

  /**
   * Performs preliminary work to configure debugger connection parameters to
   * start the Java process with. The method adds the debugger connection
   * parameters to the provided [JavaCommandLineState]. Then it returns
   * [TargetDebuggerConnection] object that could be used later to
   * resolve the connection parameters from IDE side against created
   * [TargetEnvironment].
   *
   *
   * Does nothing and returns `null` for
   * [LocalTargetEnvironmentRequest] or an executor other than
   * [DefaultDebugExecutor].
   *
   * @param javaCommandLineState the command line state that is going to be
   * modified
   * @param request              the target environment request
   * @return the constructed [TargetDebuggerConnection] object for
   * further resolution of connection parameters from IDE side or `null`
   * in the case of inappropriate [Executor] or the local type of the
   * `request`.
   */
  @JvmStatic
  fun prepareDebuggerConnection(
    javaCommandLineState: JavaCommandLineState,
    request: TargetEnvironmentRequest,
  ): TargetDebuggerConnection? {
    val javaParameters: JavaParameters = runCatching {
      javaCommandLineState.javaParameters
    }.getOrNull() ?: return null

    val remotePort = requiredDebuggerTargetPort(javaCommandLineState, request) ?: return null

    try {
      val java9plus: Boolean = request.isJava9Plus()

      val remoteAddressForVmParams: String = if (java9plus) {
        // IDEA-225182 - hack: pass "host:port" to construct correct VM params, then adjust the connection
        // IDEA-265364 - enforce ipv4 here with explicit 0.0.0.0 address
        "0.0.0.0:$remotePort"
      }
      else {
        remotePort.toString()
      }

      val remoteConnection = RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
        .suspend(true)
        .create(javaParameters)

      remoteConnection.applicationAddress = remotePort.toString()
      if (java9plus) {
        remoteConnection.applicationHostName = "*"
      }

      return TargetDebuggerConnection(remoteConnection, TargetPortBinding(null, remotePort))
    }
    catch (_: ExecutionException) {
      return null
    }
  }

  private fun TargetEnvironmentRequest.isJava9Plus(): Boolean {
    val javaVersion = configuration?.runtimes
                        ?.findByType(JavaLanguageRuntimeConfiguration::class.java)
                        ?.javaVersionString ?: return false
    if (javaVersion.isEmpty()) {
      return false
    }
    return JavaSdkVersion.fromVersionString(javaVersion)?.isAtLeast(JavaSdkVersion.JDK_1_9) ?: false
  }
}
