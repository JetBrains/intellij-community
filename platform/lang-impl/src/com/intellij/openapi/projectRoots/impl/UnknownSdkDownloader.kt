// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Consumer
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

private val LOG = logger<UnknownSdkDownloadTask>()

@ApiStatus.Internal
internal class UnknownSdkDownloadTask(
  private val info: UnknownSdk,
  private val fix: UnknownSdkDownloadableSdkFix,
  private val createSdk: Function<in SdkDownloadTask?, out Sdk>,
  private val onSdkNameReady: Consumer<in Sdk?>,
  private val onCompleted: Consumer<in Sdk?>
) {
  private val isRunning = AtomicBoolean(false)

  private fun testAndRun() : Boolean {
    if (!isRunning.compareAndSet(false, true)) return false

    if (!Registry.`is`("unknown.sdk.apply.download.fix")) {
      ApplicationManager.getApplication().invokeLater { onCompleted.consume(null) }
      return false
    }

    return true
  }

  fun runBlocking(indicator: ProgressIndicator) {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    if (!testAndRun()) return

    runImpl(indicator)
  }

  fun runAsync(project: Project?) {
    if (!testAndRun()) return

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

  private fun runImpl(indicator: ProgressIndicator) {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    var sdk: Sdk? = null
    try {
      val task = fix.createTask(indicator)
      val downloadTracker = SdkDownloadTracker.getInstance()
      sdk = createSdk.apply(task)
      downloadTracker.configureSdk(sdk, task)
      onSdkNameReady.consume(sdk)
      downloadTracker.downloadSdk(task, listOf(sdk), indicator)
    }
    catch (t: Exception) {
      sdk = null
      if (t is ControlFlowException) throw t
      throw object : RuntimeException("Failed to download ${info.sdkType.presentableName} ${fix.downloadDescription} for $info. ${t.message}", t) {
        override fun getLocalizedMessage() = ProjectBundle.message("dialog.message.failed.to.download.0.1", fix.downloadDescription, t.message)
      }
    }
    finally {
      onCompleted.consume(sdk)
    }
  }
}

object UnknownSdkDownloader {
  @JvmStatic
  @ApiStatus.Internal
  fun downloadFixAsync(project: Project?,
                       info: UnknownSdk,
                       fix: UnknownSdkDownloadableSdkFix,
                       createSdk: Function<in SdkDownloadTask?, out Sdk>,
                       onSdkNameReady: Consumer<in Sdk?>,
                       onCompleted: Consumer<in Sdk?>) {
    UnknownSdkDownloadTask(info, fix, createSdk, onSdkNameReady, onCompleted).runAsync(project)
  }
}
