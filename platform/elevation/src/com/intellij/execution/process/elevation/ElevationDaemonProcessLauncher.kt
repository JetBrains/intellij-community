// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.DaemonLaunchOptions
import com.intellij.execution.process.mediator.launcher.*
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.sun.jna.platform.unix.LibC
import java.io.IOException
import java.nio.file.Path


class ElevationDaemonProcessLauncher(clientBuilder: ProcessMediatorClient.Builder) : DaemonProcessLauncher(clientBuilder) {
  override fun createHandshakeTransport(): DaemonHandshakeTransport {
    // Unix sudo may take different forms, and not all of them are reliable in terms of process lifecycle management,
    // input/output redirection, and so on. To overcome the limitations we use an RSA-secured channel for initial communication
    // instead of process stdio, and launch it in a trampoline mode. In this mode the sudo'ed process forks the real daemon process,
    // relays the handshake message from it, and exits, so that the sudo process is done as soon as the handshake message is exchanged.
    // Using a trampoline also ensures that the launched process is certainly not a session leader, and allows it to become one.
    // In particular, this is a workaround for high CPU consumption of the osascript (used on macOS instead of sudo) process;
    // we want it to finish as soon as possible.
    return if (SystemInfo.isWindows) {
      super.createHandshakeTransport()
    }
    else try {
      openUnixHandshakeTransport()
    }
    catch (e: IOException) {
      throw ExecutionException(ElevationBundle.message("dialog.message.handshake.init.failed"), e)
    }
  }

  private fun openUnixHandshakeTransport(): DaemonHandshakeTransport {
    val launchOptions = createBaseLaunchOptions()
    return try {
      DaemonHandshakeTransport.createUnixFifoTransport(launchOptions, path = FileUtil.generateRandomTemporaryPath().toPath())
    }
    catch (e0: IOException) {
      ElevationLogger.LOG.warn("Unable to create file-based handshake channel; falling back to socket streams", e0)
      try {
        DaemonHandshakeTransport.createSocketTransport(launchOptions)
      }
      catch (e1: IOException) {
        e1.addSuppressed(e0)
        throw e1
      }
    }
      // neither a named pipe nor an open port is safe from prying eyes
      .encrypted()
  }

  override fun createBaseLaunchOptions(): DaemonLaunchOptions {
    return super.createBaseLaunchOptions().let {
      if (SystemInfo.isWindows) it
      else it.copy(trampoline = true, daemonize = true,
                   machNamespaceUid = if (SystemInfo.isMac) LibC.INSTANCE.getuid() else null)
    }
  }

  override fun createProcessHandler(transport: DaemonHandshakeTransport): BaseOSProcessHandler {
    val commandLine = createCommandLine(transport)
    val sudoCommandLine = ExecUtil.sudoCommand(commandLine,
                                               ElevationBundle.message("dialog.title.sudo.prompt.product.elevation.daemon",
                                                                       ApplicationNamesInfo.getInstance().fullProductName))
    val sudoPath = if (sudoCommandLine !== commandLine) Path.of(sudoCommandLine.exePath) else null

    return createProcessHandler(transport, sudoCommandLine).apply {
      putUserData(SUDO_PATH_KEY, sudoPath)
    }
  }

  override fun handshakeFailed(transport: DaemonHandshakeTransport,
                               processHandler: BaseOSProcessHandler,
                               output: ProcessOutput,
                               reason: @NlsContexts.DialogMessage String): Nothing {
    val sudoPath: Path? = processHandler.getUserData(SUDO_PATH_KEY)

    if (SystemInfo.isMac) {
      if (output.isExitCodeSet && output.exitCode == 1 &&
          sudoPath != null && "osascript" in sudoPath.fileName.toString() &&
          "execution error: User cancelled" in output.stderr) {
        throw ProcessCanceledException()
      }
    }

    val message = when (sudoPath) {
      null -> ElevationBundle.message("dialog.message.failed.to.launch.daemon", reason)
      else -> ElevationBundle.message("dialog.message.failed.to.launch.daemon.with.sudo", sudoPath.fileName, reason)
    }
    throw ExecutionException(message)
  }

  companion object {
    private val SUDO_PATH_KEY: Key<Path> = Key.create("SUDO_PATH_KEY")
  }
}
