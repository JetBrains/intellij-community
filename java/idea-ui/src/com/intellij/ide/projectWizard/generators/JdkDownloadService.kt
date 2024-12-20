// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JdkDownloadService(private val project: Project, private val coroutineScope: CoroutineScope) {

  @RequiresEdt
  fun setupInstallableSdk(downloadTask: SdkDownloadTask): Sdk {
    return application.runWriteAction<Sdk> {
      createDownloadSdkInternal(downloadTask)
    }
  }

  fun downloadSdk(sdk: Sdk) {
    coroutineScope.launch(Dispatchers.EDT) {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdk.name)) {
        val tracker = SdkDownloadTracker.getInstance()
        tracker.startSdkDownloadIfNeeded(sdk)
      }
    }
  }

  private fun createDownloadTask(sdkDownloadTask: JdkDownloadTask): SdkDownloadTask? {
    val (selectedFile, error) = JdkInstaller.getInstance().validateInstallDir(sdkDownloadTask.request.installDir.toString())
    if (selectedFile == null) {
      Messages.showErrorDialog(project, JavaUiBundle.message("jdk.download.error.message", error), JavaUiBundle.message("jdk.download.error.title"))
      return null
    }

    val downloadRequest = LOG.runAndLogException {
      JdkInstaller.getInstance().prepareJdkInstallation(sdkDownloadTask.jdkItem, selectedFile)
    } ?: return null

    return JdkDownloadTask(sdkDownloadTask.jdkItem, downloadRequest, project)
  }

  @RequiresWriteLock
  private fun createDownloadSdkInternal(downloadTask: SdkDownloadTask): Sdk {
    val sdkType = JavaSdk.getInstance()
    val sdkTable = ProjectJdkTable.getInstance()
    val sdks = sdkTable.allJdks.asList()
    val sdk = ProjectSdksModel.createDownloadSdkInternal(sdkType, downloadTask, sdks)
    sdkTable.addJdk(sdk)
    return sdk
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, onSdkCreated: suspend (Sdk) -> Unit = {}, ): CompletableFuture<Boolean> {
    val sdkDownloadedFuture = CompletableFuture<Boolean>()

    coroutineScope.launch(Dispatchers.EDT) {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdkDownloadTask.jdkItem.suggestedSdkName)) {
        val downloadTask = createDownloadTask(sdkDownloadTask)
        if (downloadTask == null) {
          sdkDownloadedFuture.complete(false)
          return@withBackgroundProgress
        }

        val sdk = writeAction {
          createDownloadSdkInternal(downloadTask)
        }

        onSdkCreated(sdk)

        val tracker = SdkDownloadTracker.getInstance()
        tracker.startSdkDownloadIfNeeded(sdk)
        val registered = tracker.tryRegisterDownloadingListener(sdk, project, null) {
          sdkDownloadedFuture.complete(it)
        }
        if (!registered) {
          sdkDownloadedFuture.complete(false)
        }
      }
    }

    return sdkDownloadedFuture
  }

  fun scheduleDownloadJdkForNewProject(sdkDownloadTask: JdkDownloadTask): CompletableFuture<Boolean> {
    return scheduleDownloadJdk(sdkDownloadTask) {
      writeAction {
        ProjectRootManager.getInstance(project).projectSdk = it
      }
    }
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, module: Module, isCreatingNewProject: Boolean): CompletableFuture<Boolean> {
    return if (isCreatingNewProject) {
      scheduleDownloadJdkForNewProject(sdkDownloadTask)
    } else {
      scheduleDownloadJdk(sdkDownloadTask, module)
    }
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, module: Module): CompletableFuture<Boolean> {
    return scheduleDownloadJdk(sdkDownloadTask) { sdk ->
      ModuleRootModificationUtil.setModuleSdk(module, sdk)
    }
  }

  companion object {
    private val LOG = logger<JdkDownloadService>()
  }
}