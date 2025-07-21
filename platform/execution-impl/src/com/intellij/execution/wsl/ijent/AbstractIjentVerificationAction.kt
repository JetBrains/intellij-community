// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")

package com.intellij.execution.wsl.ijent

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.eel.provider.utils.copy
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.ijent.IjentMissingBinary
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.createIjentSession
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.ByteArrayOutputStream
import java.net.URI
import kotlin.io.path.isDirectory

/**
 * Base class for internal smoke testing of IJent.
 * It was deliberately put into the core part, near WSL utilities, to be sure that IJent can be integrated into WSL support.
 */
@Internal
abstract class AbstractIjentVerificationAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.run {
      isEnabledAndVisible = ApplicationManager.getApplication().isInternal
      description = "An internal action for verifying correct module layout for IJent"
    }
  }

  @OptIn(DelicateCoroutinesApi::class)  // Doesn't matter for a trivial test utility.
  override fun actionPerformed(e: AnActionEvent) {
    val modalTaskOwner =
      e.project?.let(ModalTaskOwner::project)
      ?: PlatformDataKeys.CONTEXT_COMPONENT.getData(e.dataContext)?.let(
        ModalTaskOwner::component)
      ?: error("No ModalTaskOwner")
    GlobalScope.launch {
      LOG.runAndLogException {
        try {
          withModalProgress(modalTaskOwner, e.presentation.text, TaskCancellation.cancellable()) {
            coroutineScope {
              val (title, deployingStrategy, descriptor) = deployingStrategy(this)
              deployingStrategy.createIjentSession<IjentPosixApi>().getIjentInstance(descriptor).use { ijent ->
                coroutineScope {
                  launch {
                    val info = ijent.ijentProcessInfo
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                      Messages.showInfoMessage(
                        """
                        Architecture: ${info.architecture}
                        Remote PID:   ${info.remotePid}
                        Version:      ${info.version}
                        """.trimIndent(),
                        title
                      )
                    }
                  }

                  launch {
                    val process = ijent.exec.spawnProcess("uname", "-a").eelIt()
                    val stdout = ByteArrayOutputStream()
                    copy(process.stdout, stdout.asEelChannel()) // TODO: process errors
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                      Messages.showInfoMessage(stdout.toString(), title)
                    }
                  }

                  launch(Dispatchers.IO) {
                    val path = "/etc"
                    val isDir =
                      IjentNioFileSystemProvider.getInstance()
                        .newFileSystem(
                          URI("ijent://some-random-string"),
                          IjentNioFileSystemProvider.newFileSystemMap(ijent.fs),
                        ).use { nioFs ->
                          nioFs.getPath(path).isDirectory()
                        }
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                      Messages.showInfoMessage("$path is directory: $isDir", title)
                    }
                  }
                }
              }
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

  protected abstract suspend fun deployingStrategy(ijentProcessScope: CoroutineScope): Triple<String, IjentDeployingStrategy, EelDescriptor>

  companion object {
    protected val LOG = logger<AbstractIjentVerificationAction>()
  }
}
