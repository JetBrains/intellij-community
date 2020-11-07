// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.MetadataUtils
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

private typealias Port = Int

private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress


object ProcessMediatorDaemonLauncher {
  fun launchDaemon(sudo: Boolean): ProcessMediatorDaemon {
    val appExecutorService = AppExecutorUtil.getAppExecutorService()

    // Unix sudo may take different forms, and not all of them are reliable in terms of process lifecycle management,
    // input/output redirection, and so on. To overcome the limitations we use an RSA-secured channel for initial communication
    // instead of process stdio, and launch it in a trampoline mode. In this mode the sudo'ed process forks the real daemon process,
    // relays the initial hello from it, and exits, so that the sudo process is done as soon as the initial hello is exchanged.
    // Using a trampoline also ensures that the launched process is certainly not a session leader, and allows it to become one.
    // In particular, this is a workaround for high CPU consumption of the osascript (used on macOS instead of sudo) process;
    // we want it to finish as soon as possible.
    val helloIpc = if (SystemInfo.isWindows) {
      DaemonHelloStdoutIpc()
    }
    else try {
      appExecutorService.submitAndAwaitCloseable { openUnixHelloIpc() }
    }
    catch (e: IOException) {
      throw ExecutionException(ElevationBundle.message("dialog.message.daemon.hello.failed"), e)
    }
    val daemonLaunchOptions = helloIpc.getDaemonLaunchOptions().let {
      if (SystemInfo.isWindows) it else it.copy(trampoline = sudo, daemonize = sudo, leaderPid = ProcessHandle.current().pid())
    }

    val trampolineCommandLine = createJavaVmCommandLine(ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses())
      .withParameters(ProcessMediatorDaemonRuntimeClasspath.getMainClass().name)
      .withParameters(daemonLaunchOptions.asCmdlineArgs())

    return helloIpc.use {
      appExecutorService.submitAndAwait {
        val maybeSudoTrampolineCommandLine =
          if (!sudo) trampolineCommandLine
          else ExecUtil.sudoCommand(trampolineCommandLine, "Elevation daemon")

        val trampolineProcessHandler = helloIpc.createDaemonProcessHandler(maybeSudoTrampolineCommandLine).also {
          it.startNotify()
        }

        val daemonHello = helloIpc.readHello() ?: throw ProcessCanceledException()
        val daemonProcessHandle =
          if (SystemInfo.isWindows) trampolineProcessHandler.process.toHandle()  // can't get access a process owned by another user
          else ProcessHandle.of(daemonHello.pid).orElseThrow(::ProcessCanceledException)

        ProcessMediatorDaemonImpl(daemonProcessHandle,
                                  daemonHello.port,
                                  DaemonClientCredentials(daemonHello.token))
      }
    }
  }

  private fun openUnixHelloIpc(): DaemonHelloIpc {
    return try {
      DaemonHelloUnixFifoIpc()
    }
    catch (e0: IOException) {
      ElevationLogger.LOG.warn("Unable to create file-based hello channel; falling back to socket streams", e0)
      try {
        DaemonHelloSocketIpc()
      }
      catch (e1: IOException) {
        e1.addSuppressed(e0)
        throw e1
      }
    }
      // neither a named pipe nor an open port is safe from prying eyes
      .encrypted()
  }
}

private fun <R> ExecutorService.submitAndAwait(block: () -> R): R {
  val future = CompletableFuture.supplyAsync(block, this)
  return awaitWithCheckCanceled(future)
}

private fun <R : Closeable?> ExecutorService.submitAndAwaitCloseable(block: () -> R): R {
  val future = CompletableFuture.supplyAsync(block, this)
  return try {
    awaitWithCheckCanceled(future)
  }
  catch (e: Throwable) {
    future.whenComplete { closeable, _ -> closeable?.close() }
    throw e
  }
}

private fun <R> awaitWithCheckCanceled(future: CompletableFuture<R>): R {
  try {
    if (ApplicationManager.getApplication() == null) return future.join()
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
  }
  catch (e: Throwable) {
    throw ExceptionUtil.findCause(e, java.util.concurrent.ExecutionException::class.java)?.cause ?: e
  }
}

private class ProcessMediatorDaemonImpl(private val processHandle: ProcessHandle,
                                        private val port: Port,
                                        private val credentials: DaemonClientCredentials) : ProcessMediatorDaemon {

  override fun createChannel(): ManagedChannel {
    return ManagedChannelBuilder.forAddress(LOOPBACK_IP, port).usePlaintext()
      .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata()))
      .build().also { channel ->
        processHandle.onExit().whenComplete { _, _ -> channel.shutdown() }
      }
  }

  override fun stop() = Unit

  override fun blockUntilShutdown() {
    processHandle.onExit().get()
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
