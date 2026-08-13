// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.eel.TargetDebuggerConnectionProxy
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion

private val LOG = logger<TargetDebuggerConnectionUtil>()

internal object TargetDebuggerConnectionUtil {

  /**
   * Performs preliminary work to configure debugger connection parameters to start the Java process with. The method adds the debugger
   * connection parameters to the provided [JavaCommandLineState]. Then it returns [RemoteConnection] object that could be used later to
   * resolve the connection parameters from IDE side against created [TargetEnvironment].
   *
   * Does nothing and returns `null` for local execution request or an executor other than [DefaultDebugExecutor].
   *
   * @param javaCommandLineState the command line state that is going to be
   * modified
   * @param request              the target environment request
   * @return the constructed [RemoteConnection] object with all required parameters from IDE side or `null`
   * in the case of inappropriate [Executor] or the local type of the `request`.
   */
  @JvmStatic
  fun prepareDebuggerConnection(javaCommandLineState: JavaCommandLineState, request: TargetEnvironmentRequest): RemoteConnection? {
    if (javaCommandLineState.isDebugExecutor() && request !is LocalTargetEnvironmentRequest) {
      try {
        return prepareRemoteConnection(
          javaCommandLineState.environment.project,
          javaCommandLineState.environment,
          javaCommandLineState.javaParameters,
          request.isJava9Plus()
        )
      }
      catch (e: Exception) {
        LOG.error("Unable to prepare TargetDebuggerConnection", e)
      }
    }
    return null
  }

  private fun JavaCommandLineState.isDebugExecutor(): Boolean =
    DefaultDebugExecutor.EXECUTOR_ID.equals(environment.executor.getId(), ignoreCase = true)

  private fun prepareRemoteConnection(
    project: Project,
    disposable: Disposable,
    javaParameters: JavaParameters,
    isJava9Plus: Boolean,
  ): RemoteConnection {
    val (localPort, remotePort) = TargetDebuggerConnectionProxy.getProxy(project, disposable)
    val remoteAddressForVmParams: String = if (isJava9Plus) {
      // IDEA-225182 - hack: pass "host:port" to construct correct VM params, then adjust the connection
      "0.0.0.0:${remotePort}"
    }
    else {
      remotePort.toString()
    }

    val remoteConnection = RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
      .suspend(true)
      .create(javaParameters)

    return remoteConnection.apply {
      applicationAddress = remotePort.toString()
      debuggerAddress = localPort.toString()
      debuggerHostName = "localhost"
      if (isJava9Plus) {
        applicationHostName = "*"
      }
    }
  }

  private fun TargetEnvironmentRequest.isJava9Plus(): Boolean {
    val javaVersion = configuration?.runtimes
                        ?.findByType(JavaLanguageRuntimeConfiguration::class.java)
                        ?.javaVersionString ?: return false
    return javaVersion.isNotEmpty() && JavaSdkVersion.fromVersionString(javaVersion)?.isAtLeast(JavaSdkVersion.JDK_1_9) ?: false
  }
}
