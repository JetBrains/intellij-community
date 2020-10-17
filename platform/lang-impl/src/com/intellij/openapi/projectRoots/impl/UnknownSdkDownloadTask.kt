// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdkDownloadableSdkFix
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.util.Consumer
import com.intellij.util.EmptyConsumer
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

private val LOG = logger<UnknownSdkDownloadTask>()

@ApiStatus.Internal
internal data class UnknownSdkDownloadTask
@JvmOverloads constructor(
  private val info: UnknownSdk,
  private val fix: UnknownSdkDownloadableSdkFix,
  private val createSdk: Function<in SdkDownloadTask?, out Sdk>,
  private val onSdkNameReady: Consumer<in Sdk?> = EmptyConsumer.getInstance(),
  private val onCompleted: Consumer<in Sdk?> = EmptyConsumer.getInstance()
) {

  fun withListener(listener : UnknownSdkFixAction.Listener): UnknownSdkDownloadTask {
    val oldOnSdkNameReady = this.onSdkNameReady
    val oldOnResolved = this.onCompleted
    return copy(
      onSdkNameReady = {
        oldOnSdkNameReady.consume(it)
        it?.let { listener.onSdkNameResolved(it) }
      },
      onCompleted = {
        oldOnResolved.consume(it)
        if (it != null) {
          listener.onSdkResolved(it)
        } else {
          listener.onResolveFailed()
        }
      }
    )
  }

  fun runBlocking(indicator: ProgressIndicator) : Sdk {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    return runImpl(indicator)
  }

  fun runAsync(project: Project?) {
    val title = ProjectBundle.message("progress.title.downloading.sdk")
    object : Backgroundable(project, title, true, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        try {
          runImpl(indicator)
        } catch (t: Throwable) {
          if (t is ControlFlowException) throw t
          LOG.warn(t.message, t)
          invokeLater {
            Messages.showErrorDialog(t.localizedMessage, title)
          }
        }
      }
    }.queue()
  }

  private fun runImpl(indicator: ProgressIndicator) : Sdk {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val sdk = runCatching {
      val task = fix.createTask(indicator)
      val downloadTracker = SdkDownloadTracker.getInstance()
      val sdk = invokeAndWaitIfNeeded { createSdk.apply(task) }
      downloadTracker.configureSdk(sdk, task)
      invokeAndWaitIfNeeded { onSdkNameReady.consume(sdk) }
      downloadTracker.downloadSdk(task, listOf(sdk), indicator)
      sdk
    }
    invokeAndWaitIfNeeded { onCompleted.consume(sdk.getOrNull()) }

    return sdk.getOrElse { t ->
      if (t is ControlFlowException) throw t
      throw object : RuntimeException("Failed to download ${info.sdkType.presentableName} ${fix.downloadDescription} for $info. ${t.message}", t) {
        override fun getLocalizedMessage() = ProjectBundle.message("dialog.message.failed.to.download.0.1", fix.downloadDescription, t.message)
      }
    }
  }
}
