// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.process.elevation.ElevationBundle
import com.intellij.execution.process.elevation.ElevationLogger
import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemonRuntimeClasspath
import com.intellij.execution.process.mediator.handshake.*
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.SystemProperties
import com.intellij.util.containers.orNull
import com.intellij.util.io.BaseInputStreamReader
import com.sun.jna.platform.unix.LibC
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.selects.select
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.net.InetAddress
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

private typealias Port = Int

private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress


@Suppress("EXPERIMENTAL_API_USAGE")
class ProcessMediatorDaemonLauncher(val sudo: Boolean) {
  fun launchDaemon(): ProcessMediatorDaemon {
    return GlobalScope.async(Dispatchers.IO) {
      createHandshakeTransport().use { handshakeTransport ->
        ensureActive()

        val daemonLaunchOptions = handshakeTransport.getDaemonLaunchOptions()
        val daemonCommandLine = createLauncherCommandLine(daemonLaunchOptions)
        val launcherCommandLine =
          if (!sudo) daemonCommandLine
          else ExecUtil.sudoCommand(daemonCommandLine, ElevationBundle.message("dialog.title.sudo.prompt.product.elevation.daemon",
                                                                               ApplicationNamesInfo.getInstance().fullProductName))
        val sudoPath = if (launcherCommandLine !== daemonCommandLine) Path.of(launcherCommandLine.exePath) else null

        val launcherProcessHandler = handshakeTransport.createDaemonProcessHandler(launcherCommandLine)
        val handshake = launcherProcessHandler.withOutputCaptured(SynchronizedProcessOutput()) { launcherOutput ->
          startNotify()

          val handshakeAsync = async(Dispatchers.IO) { handshakeTransport.readHandshake() }
          val finishedAsync = launcherOutput.onFinished().asDeferred()

          try {
            select<Handshake?> {
              handshakeAsync.onAwait { it }
              finishedAsync.onAwait { handshakeFailed(it, sudoPath) }
            }
            // premature EOF; give the launcher a chance to exit cleanly and collect the whole output
            ?: select<Nothing> {
              finishedAsync.onAwait { handshakeFailed(it, sudoPath) }
              onTimeout(1000) { launcherOutput.setTimeout(); handshakeFailed(launcherOutput, sudoPath) }
            }
          }
          catch (e: IOException) {
            handshakeFailed(launcherOutput, sudoPath, e)
          }
        }
        val daemonProcessHandle = ProcessHandle.of(handshake.pid).orNull()
                                  // Use the launcher process handle instead unless it's a short-living trampoline.
                                  // In particular, this happens on Windows, where we can't access a process owned by another user.
                                  ?: launcherProcessHandler.process.toHandle().takeUnless { daemonLaunchOptions.trampoline }

        ProcessMediatorDaemonImpl(daemonProcessHandle,
                                  handshake.port,
                                  DaemonClientCredentials(handshake.token))
      }
    }.awaitWithCheckCanceled()
  }

  private fun handshakeFailed(output: ProcessOutput, sudoPath: Path?, error: IOException? = null): Nothing = synchronized(output) {
    val stderr = output.stderr
    val errorExit =
      if (output.isExitCodeSet && output.exitCode != 0) ProcessTerminatedListener.stringifyExitCode(output.exitCode)
      else null

    ElevationLogger.LOG.warn("Reading handshake failed", error)
    if (errorExit != null) {
      ElevationLogger.LOG.warn("Daemon process finished with exit code $errorExit")
    }
    ElevationLogger.LOG.warn("Daemon process stderr:\n$stderr")

    if (SystemInfo.isMac) {
      if (output.isExitCodeSet && output.exitCode == 1 &&
          sudoPath != null && "osascript" in sudoPath.fileName.toString() &&
          "execution error: User cancelled" in stderr) {
        throw ProcessCanceledException()
      }
    }

    val reason = when {
      errorExit != null -> IdeBundle.message("finished.with.exit.code.text.message", errorExit)
      error == null -> ElevationBundle.message("dialog.message.failed.to.launch.daemon.handshake.eof")
      else -> ElevationBundle.message("dialog.message.failed.to.launch.daemon.handshake.ioe")
    }
    val message = when (sudoPath) {
      null -> ElevationBundle.message("dialog.message.failed.to.launch.daemon", reason)
      else -> ElevationBundle.message("dialog.message.failed.to.launch.daemon.with.sudo", sudoPath.fileName, reason)
    }
    throw ExecutionException(message)
  }

  private fun createHandshakeTransport(): HandshakeTransport {
    // Unix sudo may take different forms, and not all of them are reliable in terms of process lifecycle management,
    // input/output redirection, and so on. To overcome the limitations we use an RSA-secured channel for initial communication
    // instead of process stdio, and launch it in a trampoline mode. In this mode the sudo'ed process forks the real daemon process,
    // relays the handshake message from it, and exits, so that the sudo process is done as soon as the handshake message is exchanged.
    // Using a trampoline also ensures that the launched process is certainly not a session leader, and allows it to become one.
    // In particular, this is a workaround for high CPU consumption of the osascript (used on macOS instead of sudo) process;
    // we want it to finish as soon as possible.
    return if (SystemInfo.isWindows) {
      HandshakeTransport.createProcessStdoutTransport(DaemonLaunchOptions())
    }
    else try {
      openUnixHandshakeTransport()
    }
    catch (e: IOException) {
      throw ExecutionException(ElevationBundle.message("dialog.message.handshake.init.failed"), e)
    }
  }

  private fun openUnixHandshakeTransport(): HandshakeTransport {
    val launchOptions = DaemonLaunchOptions(trampoline = sudo, daemonize = sudo,
                                            leaderPid = ProcessHandle.current().pid(),
                                            machNamespaceUid = if (SystemInfo.isMac) LibC.INSTANCE.getuid() else null)
    return try {
      HandshakeTransport.createUnixFifoTransport(launchOptions, path = FileUtil.generateRandomTemporaryPath().toPath())
    }
    catch (e0: IOException) {
      ElevationLogger.LOG.warn("Unable to create file-based handshake channel; falling back to socket streams", e0)
      try {
        HandshakeTransport.createSocketTransport(launchOptions)
      }
      catch (e1: IOException) {
        e1.addSuppressed(e0)
        throw e1
      }
    }
      // neither a named pipe nor an open port is safe from prying eyes
      .encrypted()
  }

  private fun createLauncherCommandLine(daemonLaunchOptions: DaemonLaunchOptions): GeneralCommandLine {
    return createJavaVmCommandLine(ProcessMediatorDaemonRuntimeClasspath.getProperties(),
                                   ProcessMediatorDaemonRuntimeClasspath.getClasspathClasses())
      .withParameters(ProcessMediatorDaemonRuntimeClasspath.getMainClass().name)
      .withParameters(daemonLaunchOptions.asCmdlineArgs())
  }

  private fun HandshakeTransport.createDaemonProcessHandler(daemonCommandLine: GeneralCommandLine): BaseOSProcessHandler {
    val processHandler =
      if (this !is ProcessStdoutHandshakeTransport) {
        OSProcessHandler.Silent(daemonCommandLine)
      }
      else {
        object : OSProcessHandler.Silent(daemonCommandLine) {
          override fun createProcessOutReader(): Reader {
            return BaseInputStreamReader(InputStream.nullInputStream())  // don't let the process handler touch the stdout stream
          }
        }.also {
          initStream(it.process.inputStream)
        }
      }
    processHandler.addProcessListener(object : ProcessAdapter() {
      override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
        ElevationLogger.LOG.info("Daemon/launcher [$outputType]: ${event.text.removeSuffix("\n")}")
      }

      override fun processTerminated(event: ProcessEvent) {
        val exitCodeString = ProcessTerminatedListener.stringifyExitCode(event.exitCode)
        ElevationLogger.LOG.info("Daemon/launcher process terminated with exit code ${exitCodeString}")
      }
    })
    return processHandler
  }
}


private fun <R> Deferred<R>.awaitWithCheckCanceled(): R = asCompletableFuture().awaitWithCheckCanceled()
private fun <R> CompletableFuture<R>.awaitWithCheckCanceled(): R {
  try {
    if (ApplicationManager.getApplication() == null) return join()
    return ProgressIndicatorUtils.awaitWithCheckCanceled(this)
  }
  catch (e: Throwable) {
    throw ExceptionUtil.findCause(e, java.util.concurrent.ExecutionException::class.java)?.cause ?: e
  }
}


private inline fun <P : ProcessHandler, T : ProcessOutput, R> P.withOutputCaptured(output: T, block: P.(T) -> R): R {
  val capturingProcessListener = CapturingProcessAdapter(output)
  addProcessListener(capturingProcessListener)
  return try {
    block(output)
  }
  finally {
    removeProcessListener(capturingProcessListener)
  }
}


private class ProcessMediatorDaemonImpl(private val processHandle: ProcessHandle?,
                                        private val port: Port,
                                        private val credentials: DaemonClientCredentials) : ProcessMediatorDaemon {

  override fun createChannel(): ManagedChannel {
    return ManagedChannelBuilder.forAddress(LOOPBACK_IP, port).usePlaintext()
      .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata()))
      .build().also { channel ->
        processHandle?.onExit()?.whenComplete { _, _ -> channel.shutdown() }
      }
  }

  override fun stop() = Unit

  override fun blockUntilShutdown() {
    processHandle?.onExit()?.get()
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
