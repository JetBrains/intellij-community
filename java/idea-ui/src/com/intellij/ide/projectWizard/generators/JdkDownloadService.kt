// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

private val LOG = logger<JdkDownloadService>()

private fun Sdk.describe(): String = "'$name' (home: $homePath, id: ${System.identityHashCode(this)})"

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JdkDownloadService(private val project: Project, private val coroutineScope: CoroutineScope) {

  companion object {
    @ApiStatus.Internal
    @RequiresEdt
    fun setupInstallableSdk(downloadTask: SdkDownloadTask): Sdk {
      return application.runWriteAction<Sdk> {
        JdkDownloadUtil.findOrCreateDownloadSdkInternal(JavaSdk.getInstance(), downloadTask)
      }
    }
  }

  fun scheduleDownloadSdk(sdk: Sdk?): CompletableFuture<Boolean> {
    val deferred = coroutineScope.async {
      if (sdk == null) return@async false
      if (!SdkDownloadTracker.getInstance().isDownloading(sdk)) {
        if (sdk.homePath?.let { !Files.exists(Path.of(it)) } == true) {
          LOG.info("The SDK ${sdk.describe()} has no pending download")
        }
        return@async false
      }
      JdkDownloadUtil.downloadSdk(project, sdk)
    }
    deferred.invokeOnCompletion { cause ->
      if (cause != null) LOG.info("The download job of the SDK ${sdk?.describe()} stopped", cause)
    }
    return deferred.asCompletableFuture()
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, onJdkCreated: suspend (Sdk) -> Unit = {}): CompletableFuture<Boolean> {
    return coroutineScope.async {
      val downloadTask = JdkDownloadUtil.createDownloadTask(project, sdkDownloadTask.jdkItem, sdkDownloadTask.request.installDir)
                         ?: return@async false

      val sdk = JdkDownloadUtil.createDownloadSdk(JavaSdk.getInstance(), downloadTask)

      onJdkCreated(sdk)

      JdkDownloadUtil.downloadSdk(project, sdk)
    }.asCompletableFuture()
  }

  fun scheduleDownloadJdkForNewProject(sdkDownloadTask: JdkDownloadTask): CompletableFuture<Boolean> {
    return scheduleDownloadJdk(sdkDownloadTask) {
      edtWriteAction {
        ProjectRootManager.getInstance(project).projectSdk = it
      }
    }
  }
}