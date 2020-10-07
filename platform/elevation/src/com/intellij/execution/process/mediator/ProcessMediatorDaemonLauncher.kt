// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.*


internal fun startProcessMediatorDaemon(coroutineScope: CoroutineScope): ProcessMediatorClient {
  val host = if (java.lang.Boolean.getBoolean("java.net.preferIPv6Addresses")) "::1" else "127.0.0.1"
  val port = 50051

  val daemonCommandLine = createProcessMediatorDaemonCommandLine(host, port.toString())
  val daemonProcessHandler = OSProcessHandler.Silent(daemonCommandLine).apply {
    addProcessListener(object : ProcessAdapter() {
      override fun processTerminated(event: ProcessEvent) {
        println("Daemon exited with code ${event.exitCode}")
      }

      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        println("Daemon [$outputType]: ${event.text}")
      }
    })
  }
  daemonProcessHandler.startNotify()

  val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
  return ProcessMediatorClient(coroutineScope, channel)
}

private fun createProcessMediatorDaemonCommandLine(host: String, port: String): GeneralCommandLine {
  val processMediatorClass = ProcessMediatorDaemonRuntimeClasspath.getMainClass().name
  val classpathClasses = ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses()
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)
  val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"

  return GeneralCommandLine(javaVmExecutablePath)
    .withParameters("-cp", classpath)
    .withParameters(processMediatorClass)
    .withParameters(host, port)
}

private fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}
