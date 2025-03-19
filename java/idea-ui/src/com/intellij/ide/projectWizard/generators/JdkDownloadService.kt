// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JdkDownloadService(private val project: Project, private val coroutineScope: CoroutineScope) {

  @RequiresEdt
  fun setupInstallableSdk(downloadTask: SdkDownloadTask): Sdk {
    return application.runWriteAction<Sdk> {
      JdkDownloadUtil.createDownloadSdkInternal(JavaSdk.getInstance(), downloadTask)
    }
  }

  fun downloadSdk(sdk: Sdk) {
    coroutineScope.launch {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdk.name)) {
        JdkDownloadUtil.downloadSdk(sdk)
      }
    }
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, onJdkCreated: suspend (Sdk) -> Unit = {}): CompletableFuture<Boolean> {
    return coroutineScope.async {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdkDownloadTask.suggestedSdkName)) {

        val downloadTask = JdkDownloadUtil.createDownloadTask(project, sdkDownloadTask.jdkItem, sdkDownloadTask.request.installDir)
                           ?: return@withBackgroundProgress false

        val sdk = JdkDownloadUtil.createDownloadSdk(JavaSdk.getInstance(), downloadTask)

        onJdkCreated(sdk)

        JdkDownloadUtil.downloadSdk(sdk)
      }
    }.asCompletableFuture()
  }

  fun scheduleDownloadJdkForNewProject(sdkDownloadTask: JdkDownloadTask): CompletableFuture<Boolean> {
    return scheduleDownloadJdk(sdkDownloadTask) {
      edtWriteAction {
        ProjectRootManager.getInstance(project).projectSdk = it
      }
    }
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, module: Module, isCreatingNewProject: Boolean): CompletableFuture<Boolean> {
    return if (isCreatingNewProject) {
      scheduleDownloadJdkForNewProject(sdkDownloadTask)
    }
    else {
      scheduleDownloadJdk(sdkDownloadTask, module)
    }
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, module: Module): CompletableFuture<Boolean> {
    return scheduleDownloadJdk(sdkDownloadTask) { sdk ->
      ModuleRootModificationUtil.setModuleSdk(module, sdk)
    }
  }
}