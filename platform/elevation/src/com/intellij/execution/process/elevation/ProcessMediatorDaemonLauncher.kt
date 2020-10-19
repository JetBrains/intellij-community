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
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.MetadataUtils
import java.io.Closeable
import java.io.File
import java.net.InetAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

private typealias Port = Int

private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress


object ProcessMediatorDaemonLauncher {
  fun launchDaemon(sudo: Boolean): ProcessMediatorDaemon {
    val appExecutorService = AppExecutorUtil.getAppExecutorService()

    val helloIpc = appExecutorService.submitAndAwaitCloseable { tryCreateHelloIpc() }
                   ?: throw ExecutionException(ElevationBundle.message("dialog.message.daemon.hello.failed"))

    return helloIpc.use {
      appExecutorService.submitAndAwait {
        val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
          initialize(1024)
        }.genKeyPair()

        val daemonCommandLine = createJavaVmCommandLine(ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses())
          .withParameters(ProcessMediatorDaemonRuntimeClasspath.getMainClass().name)
          .withParameters("--token-encrypt-rsa", Base64.getEncoder().encodeToString(keyPair.public.encoded))
          .let(helloIpc::patchDaemonCommandLine)
          .let {
            if (!sudo) it else ExecUtil.sudoCommand(it, "Elevation daemon")
          }

        val daemonProcessHandler = helloIpc.createDaemonProcessHandler(daemonCommandLine).also {
          it.startNotify()
        }

        val daemonHello = helloIpc.readHello()
                          ?: throw ProcessCanceledException()

        val credentials = DaemonClientCredentials.rsaDecrypt(daemonHello.token, keyPair.private)
        ProcessMediatorDaemonImpl(daemonProcessHandler.process,
                                  daemonHello.port,
                                  credentials)
      }
    }
  }

  private fun tryCreateHelloIpc(): DaemonHelloIpc? =
    kotlin.runCatching {
      if (SystemInfo.isWindows) {
        DaemonHelloStdoutIpc()
      }
      else {
        DaemonHelloUnixFifoIpc()
      }
    }.onFailure { e ->
      ElevationLogger.LOG.warn("Unable to create file-based hello channel; falling back to socket streams", e)
    }.recoverCatching {
      DaemonHelloSocketIpc()
    }.getOrLogException(ElevationLogger.LOG)
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
  if (ApplicationManager.getApplication() == null) return future.join()
  return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
}

private class ProcessMediatorDaemonImpl(private val process: Process,
                                        private val port: Port,
                                        private val credentials: DaemonClientCredentials) : ProcessMediatorDaemon {

  override fun createChannel(): ManagedChannel {
    return ManagedChannelBuilder.forAddress(LOOPBACK_IP, port).usePlaintext()
      .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata()))
      .build()
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
