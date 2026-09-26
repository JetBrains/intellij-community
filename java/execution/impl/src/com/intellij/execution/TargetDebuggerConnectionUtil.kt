// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.eel.Mapping
import com.intellij.execution.eel.TargetDebuggerConnectionProxy
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.registry.Registry

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
    if (Registry.`is`("debugger.target.remote.client", true)) {
      val mapping = TargetDebuggerConnectionProxy.getProxy(project, false, disposable)
      return RemoteConnectionBuilder(true, DebuggerSettings.SOCKET_TRANSPORT, mapping.remote.toString())
        .suspend(true)
        .create(javaParameters)
        .setRemoteClientMapping(mapping)
        .setApplicationHostname(isJava9Plus)
    }
    else {
      val mapping = TargetDebuggerConnectionProxy.getProxy(project, true, disposable)
      return RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, getRemoteServerListenAddress(mapping, isJava9Plus))
        .suspend(true)
        .create(javaParameters)
        .setRemoteServerMapping(mapping)
        .setApplicationHostname(isJava9Plus)
    }
  }

  private fun getRemoteServerListenAddress(mapping: Mapping, isJava9Plus: Boolean): String {
    return if (isJava9Plus) {
      // IDEA-225182 - hack: pass "host:port" to construct correct VM params, then adjust the connection
      if (Registry.`is`("debugger.target.listen.any.address", true)) {
        "0.0.0.0:${mapping.remote}"
      }
      else {
        "127.0.0.1:${mapping.remote}"
      }
    }
    else {
      mapping.remote.toString()
    }
  }

  private fun RemoteConnection.setApplicationHostname(isJava9Plus: Boolean): RemoteConnection {
    if (isJava9Plus) {
      applicationHostName = "*"
    }
    return this
  }

  private fun RemoteConnection.setRemoteServerMapping(mapping: Mapping): RemoteConnection {
    applicationAddress = mapping.remote.toString()
    debuggerAddress = mapping.local.toString()
    debuggerHostName = "localhost"
    return this
  }

  private fun RemoteConnection.setRemoteClientMapping(mapping: Mapping): RemoteConnection {
    applicationAddress = mapping.local.toString()
    debuggerAddress = mapping.local.toString()
    debuggerHostName = "127.0.0.1"
    return this
  }

  private fun TargetEnvironmentRequest.isJava9Plus(): Boolean {
    val javaVersion = configuration?.runtimes
                        ?.findByType(JavaLanguageRuntimeConfiguration::class.java)
                        ?.javaVersionString ?: return false
    return javaVersion.isNotEmpty() && JavaSdkVersion.fromVersionString(javaVersion)?.isAtLeast(JavaSdkVersion.JDK_1_9) ?: false
  }
}
