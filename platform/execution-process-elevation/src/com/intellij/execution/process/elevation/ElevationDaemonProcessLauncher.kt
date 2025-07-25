// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.elevation

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.BaseOSProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.client.launcher.*
import com.intellij.execution.process.mediator.common.DaemonLaunchOptions
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
    var sudoCommandLine = ExecUtil.sudoCommand(commandLine,
                                               ElevationBundle.message("dialog.title.sudo.prompt.product.elevation.daemon",
                                                                       ApplicationNamesInfo.getInstance().fullProductName))
    val sudoPath = if (sudoCommandLine !== commandLine) Path.of(sudoCommandLine.exePath) else null

    if (sudoCommandLine.exePath == "pkexec") {
      // Pkexec loops through all file descriptors < _SC_OPEN_MAX and marks
      // them with FD_CLOEXEC before executing a command [1].
      // On Ubuntu 25 _SC_OPEN_MAX=1073741816.
      // Doing this many syscalls takes a few minutes (CPP-45629).
      //
      // This activity is redundant: when java starts a child process, it
      // marks file descriptors with FD_CLOEXEC itself [2].
      //
      // To avoid a slow pkexec, we precede it with ulimit setting a
      // small number of files to close.
      //
      // [1] https://github.com/polkit-org/polkit/blob/11c4a81f6f732e4b1887a96cab69a1ad6a000e00/src/programs/pkexec.c#L259-L267
      // [2] https://github.com/openjdk/jdk/blob/9e209fef86fe75fb09734c9112fd1d8490c22413/src/java.base/unix/native/libjava/childproc.c#L410
      val pkExecCommand = "pkexec " + sudoCommandLine.parametersList.parameters.joinToString(separator = " ")
      val ulimitPkExec = "ulimit -n 1024 && $pkExecCommand"
      sudoCommandLine = GeneralCommandLine("sh", "-c", ulimitPkExec)
    }
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
