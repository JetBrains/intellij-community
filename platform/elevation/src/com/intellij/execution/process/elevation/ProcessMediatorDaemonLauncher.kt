// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.mediator.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.execution.process.mediator.util.blockingGet
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.util.*


internal val DEFAULT_HOST = if (java.lang.Boolean.getBoolean("java.net.preferIPv6Addresses")) "::1" else "127.0.0.1"
internal const val DEFAULT_PORT = 50051

internal fun launchDaemon(coroutineScope: CoroutineScope, sudo: Boolean): ProcessMediatorClient {
  val (idePort, daemonPortDeferred) = initCommunication()

  val daemonCommandLine = createProcessMediatorDaemonCommandLine(DEFAULT_HOST, idePort).let {
    if (!sudo) it else ExecUtil.sudoCommand(it, "Elevation daemon")
  }

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

  val daemonPort = daemonPortDeferred.blockingGet()

  return startProcessMediatorClient(coroutineScope, DEFAULT_HOST, daemonPort)
}

private fun initCommunication(): Pair<Int, Deferred<Int>> {
  val serverSocket = ServerSocket(0)
  val daemonPortDeferred = GlobalScope.async(Dispatchers.IO) {
    serverSocket.accept().use { socket ->
      Scanner(socket.getInputStream().reader(Charsets.UTF_8)).use { scanner ->
        try {
          scanner.nextInt()
        }
        catch (e: NoSuchElementException) {
          throw IOException(e)
        }
      }
    }
  }
  daemonPortDeferred.invokeOnCompletion {
    serverSocket.close()
  }
  return serverSocket.localPort to daemonPortDeferred
}

internal fun startProcessMediatorClient(coroutineScope: CoroutineScope,
                                        host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT): ProcessMediatorClient {
  val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
  return ProcessMediatorClient(coroutineScope, channel)
}

internal fun createProcessMediatorDaemonCommandLine(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT): GeneralCommandLine {
  val processMediatorClass = ProcessMediatorDaemonRuntimeClasspath.getMainClass().name
  val classpathClasses = ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses()
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)
  val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"

  return GeneralCommandLine(javaVmExecutablePath)
    .withParameters("-cp", classpath)
    .withParameters(processMediatorClass)
    .withParameters(host, port.toString())
}

private fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}
