// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.runSuspendingAction
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.io.File
import java.net.InetAddress
import java.util.*

private typealias Port = Int

private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress


object ProcessMediatorDaemonLauncher {
  fun launchDaemon(sudo: Boolean): ProcessMediatorDaemon = runSuspendingAction {
    UnixFifoDaemonHelloIpc.create(this).use { helloIpc ->
      val daemonCommandLine = createJavaVmCommandLine(ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses())
        .withParameters(ProcessMediatorDaemonRuntimeClasspath.getMainClass().name)
        .let(helloIpc::patchDaemonCommandLine)
        .let {
          if (!sudo) it else ExecUtil.sudoCommand(it, "Elevation daemon")
        }

      val daemonProcessHandler = helloIpc.createDaemonProcessHandler(daemonCommandLine).also {
        it.startNotify()
      }

      val daemonHello = helloIpc.consumeDaemonHello()

      ProcessMediatorDaemonImpl(daemonProcessHandler.process, daemonHello.port)
    }
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
