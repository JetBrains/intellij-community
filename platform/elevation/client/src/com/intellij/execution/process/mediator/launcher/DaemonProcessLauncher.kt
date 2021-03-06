// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.launcher

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.daemon.DaemonProcessRuntimeClasspath
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.containers.orNull
import com.intellij.util.io.processHandshake.ProcessHandshakeLauncher
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2


open class DaemonProcessLauncher(
  private val clientBuilder: ProcessMediatorClient.Builder,
) : ProcessHandshakeLauncher<Handshake, DaemonHandshakeTransport, ProcessMediatorConnection>() {

  override fun handshakeSucceeded(handshake: Handshake,
                                  transport: DaemonHandshakeTransport,
                                  processHandler: BaseOSProcessHandler): ProcessMediatorConnection {
    val daemonProcessHandle = ProcessHandle.of(handshake.pid).orNull()
                              // Use the launcher process handle instead unless it's a short-living trampoline.
                              // In particular, this happens on Windows, where we can't access a process owned by another user.
                              ?: processHandler.process.toHandle().takeUnless { transport.getDaemonLaunchOptions().trampoline }

    val credentials = DaemonClientCredentials(handshake.token)

    return ProcessMediatorConnection.createDaemonConnection(daemonProcessHandle, handshake.port, credentials, clientBuilder)
  }

  override fun createHandshakeTransport(): DaemonHandshakeTransport {
    return DaemonHandshakeTransport.createProcessStdoutTransport(createBaseLaunchOptions())
  }

  protected open fun createBaseLaunchOptions(): DaemonLaunchOptions {
    return DaemonLaunchOptions(leaderPid = ProcessHandle.current().pid().takeIf { SystemInfo.isUnix })
  }

  protected fun createCommandLine(transport: DaemonHandshakeTransport): GeneralCommandLine {
    val daemonLaunchOptions = transport.getDaemonLaunchOptions()
    return createJavaVmCommandLine(
      DaemonProcessRuntimeClasspath.getProperties(),
      DaemonProcessRuntimeClasspath.getClasspathClasses())
      .withParameters(DaemonProcessRuntimeClasspath.getMainClass().name)
      .withParameters(daemonLaunchOptions.asCmdlineArgs())
  }

  override fun createProcessHandler(transport: DaemonHandshakeTransport): BaseOSProcessHandler {
    val commandLine = createCommandLine(transport)
    return createProcessHandler(transport, commandLine)
  }
}


private fun createJavaVmCommandLine(properties: Map<String, String>,
                                    classpathClasses: MutableList<Class<*>>): GeneralCommandLine {
  val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"
  val propertyArgs = properties.map { (k, v) -> "-D$k=$v" }
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)

  return GeneralCommandLine(javaVmExecutablePath)
    .withParameters(propertyArgs)
    .withParameters("-cp", classpath)
}

private fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}
