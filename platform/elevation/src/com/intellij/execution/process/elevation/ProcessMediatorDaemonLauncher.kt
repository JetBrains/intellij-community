// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.execution.process.mediator.util.blockingGet
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.*

private typealias Port = Int

private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress


object ProcessMediatorDaemonLauncher {
  fun launchDaemon(sudo: Boolean): ProcessMediatorDaemon {
    val (idePort, daemonPortDeferred) = initCommunication()
    val daemonCommandLine = createJavaVmCommandLine(ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses())
      .withParameters(ProcessMediatorDaemonRuntimeClasspath.getMainClass().name)
      .withParameters(LOOPBACK_IP, idePort.toString()).let {
        if (!sudo) it else ExecUtil.sudoCommand(it, "Elevation daemon")
      }
    val daemonProcessHandler = OSProcessHandler.Silent(daemonCommandLine).apply {
      addProcessListener(object : ProcessAdapter() {
        override fun processTerminated(event: ProcessEvent) {
          println("Daemon exited with code ${event.exitCode}")
        }

        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
          print("Daemon [$outputType]: ${event.text}")
        }
      })
    }
    daemonProcessHandler.startNotify()
    return ProcessMediatorDaemonImpl(daemonProcessHandler.process, daemonPortDeferred.blockingGet())
  }

  private fun initCommunication(): Pair<Port, Deferred<Port>> {
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
}

private class ProcessMediatorDaemonImpl(private val process: Process,
                                        private val port: Port) : ProcessMediatorDaemon {
  override fun createChannel(): ManagedChannel {
    return ManagedChannelBuilder.forAddress(LOOPBACK_IP, port).usePlaintext().build()
  }

  override fun stop() {
    process.destroy()
  }

  override fun blockUntilShutdown() {
    process.waitFor()
  }
}


private fun createJavaVmCommandLine(classpathClasses: MutableList<Class<*>>): GeneralCommandLine {
  val javaVmExecutablePath = SystemProperties.getJavaHome() + File.separator + "bin" + File.separator + "java"
  val classpath = classpathClasses.mapNotNullTo(LinkedHashSet()) { it.getResourcePath() }.joinToString(File.pathSeparator)

  return GeneralCommandLine(javaVmExecutablePath)
    .withPropertyInherited("java.net.preferIPv4Stack")
    .withPropertyInherited("java.net.preferIPv6Addresses")
    .withPropertyInherited("java.util.logging.config.file")
    .withParameters("-cp", classpath)
}

private fun GeneralCommandLine.withPropertyInherited(propertyName: String): GeneralCommandLine = apply {
  System.getProperty(propertyName)?.let { value ->
    addParameter("-D$propertyName=$value")
  }
}

private fun Class<*>.getResourcePath(): String? {
  return FileUtil.toCanonicalPath(PathManager.getResourceRoot(this, "/" + name.replace('.', '/') + ".class"))
}
