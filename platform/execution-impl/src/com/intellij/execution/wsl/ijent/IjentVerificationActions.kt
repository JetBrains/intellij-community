// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")

package com.intellij.execution.wsl.ijent

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.TaskCancellation
import com.intellij.openapi.progress.withModalProgress
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ijent.*
import com.intellij.platform.ijent.fs.nio.asNioFileSystem
import com.intellij.util.childScope
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory

class IjentWslVerificationAction : AbstractIjentVerificationAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.run {
      text = "Test IJent + WSL"
      isEnabled = isEnabled && WslDistributionManager.getInstance().installedDistributions.isNotEmpty()
    }
  }

  override suspend fun launchIjent(childScope: CoroutineScope): Pair<IjentApi, String> {
    val wslDistribution = WslDistributionManager.getInstance().installedDistributions.first()
    val ijent = deployAndLaunchIjent(
      ijentCoroutineScope = childScope,
      project = null,
      wslDistribution = wslDistribution,
    )
    return ijent to "IJent on $wslDistribution: uname -a"
  }
}

class IjentDockerVerificationAction : AbstractIjentVerificationAction() {
  private val dockerImage = "ubuntu:22.04"

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = "Test IJent + Docker $dockerImage"
  }

  override suspend fun launchIjent(childScope: CoroutineScope): Pair<IjentApi, String> {
    val containerName = "ijent-test-${LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)}"

    childScope.launch {
      try {
        awaitCancellation()
      }
      finally {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
          ProcessBuilder().command("docker", "rm", "-vf", containerName).start()
        }
      }
    }

    val targetPlatform = when (val arch = CpuArch.CURRENT) {
      CpuArch.ARM64 -> IjentExecFileProvider.SupportedPlatform.AARCH64__LINUX

      CpuArch.X86_64 -> IjentExecFileProvider.SupportedPlatform.X86_64__LINUX

      null, CpuArch.X86, CpuArch.ARM32, CpuArch.OTHER, CpuArch.UNKNOWN -> error("Unsupported CPU arch: $arch")
    }

    val ijentLinuxBinary = IjentExecFileProvider.getIjentBinary(targetPlatform)

    val process = withContext(Dispatchers.IO) {
      ProcessBuilder()
        .command(
          "docker",
          "run",
          "--interactive",
          "--rm",
          "--volume",
          "${ijentLinuxBinary.toBindMount()}:/ijent:ro",
          "--name",
          containerName,
          dockerImage,
          *getIjentGrpcArgv("/ijent").toTypedArray(),
        )
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()
    }

    return IjentSessionProvider.connect(childScope, process) to "IJent on Docker $dockerImage"
  }

  /**
   * https://docs.docker.com/desktop/troubleshoot/topics/#path-conversion-on-windows
   */
  private fun Path.toBindMount(): String =
    if (SystemInfo.isWindows)
      absolutePathString()
        .replace("\\", "/")
        .let { "/${it.substring(0, 1).lowercase()}${it.substring(2)}" }
    else
      absolutePathString()
}

abstract class AbstractIjentVerificationAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.run {
      isEnabledAndVisible = ApplicationManager.getApplication().isInternal
      description = "An internal action for verifiying correct module layout for IJent"
    }
  }

  @OptIn(DelicateCoroutinesApi::class)  // Doesn't matter for a trivial test utility.
  override fun actionPerformed(e: AnActionEvent) {
    val modalTaskOwner =
      e.project?.let(ModalTaskOwner::project)
      ?: PlatformDataKeys.CONTEXT_COMPONENT.getData(e.dataContext)?.let(ModalTaskOwner::component)
      ?: error("No ModalTaskOwner")
    GlobalScope.launch {
      LOG.runAndLogException {
        try {
          withModalProgress(modalTaskOwner, e.presentation.text, TaskCancellation.cancellable()) {
            coroutineScope {
              val (ijent, title) = launchIjent(childScope())

              coroutineScope {
                launch {
                  val process = when (val p = ijent.executeProcess("uname", "-a")) {
                    is IjentApi.ExecuteProcessResult.Failure -> error(p)
                    is IjentApi.ExecuteProcessResult.Success -> p.process
                  }
                  val stdout = ByteArrayOutputStream()
                  process.stdout.consumeEach(stdout::write)
                  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    Messages.showInfoMessage(stdout.toString(), title)
                  }
                }

                launch(Dispatchers.IO) {
                  val nioFs = ijent.fs.asNioFileSystem()
                  val path = "/etc"
                  val isDir = nioFs.getPath(path).isDirectory()
                  withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    Messages.showInfoMessage("$path is directory: $isDir", title)
                  }
                }
              }
              coroutineContext.cancelChildren()
            }
          }
        }
        catch (err: IjentMissingBinary) {
          GlobalScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            Messages.showErrorDialog(err.localizedMessage, "IJent error")
          }
        }
      }
    }
  }

  protected abstract suspend fun launchIjent(childScope: CoroutineScope): Pair<IjentApi, String>

  companion object {
    protected val LOG = logger<AbstractIjentVerificationAction>()
  }
}