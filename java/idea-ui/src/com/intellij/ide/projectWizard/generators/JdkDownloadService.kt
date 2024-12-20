// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
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
    val model = ProjectStructureConfigurable.getInstance(project).projectJdksModel
    return model.createIncompleteSdk(JavaSdkImpl.getInstance(), downloadTask) {
      application.runWriteAction {
        ProjectJdkTable.getInstance().addJdk(it)
      }
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

  private fun scheduleSetupInstallableSdk(
    project: Project,
    downloadTask: SdkDownloadTask,
    sdkDownloadedFuture: CompletableFuture<Boolean>,
    setSdk: (Sdk?) -> Unit,
  ) {
    val model = ProjectStructureConfigurable.getInstance(project).projectJdksModel
    coroutineScope.launch(Dispatchers.EDT) {
      val sdk = model.createIncompleteSdk(JavaSdkImpl.getInstance(), downloadTask) { sdk ->
        application.runWriteAction {
          ProjectJdkTable.getInstance().addJdk(sdk)
          setSdk(sdk)
        }
      }
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

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, onInstalledSdk: (Sdk?) -> Unit = {}): CompletableFuture<Boolean> {
    val jdkDownloader = (SdkDownload.EP_NAME.findFirstSafe { it is JdkDownloader } as? JdkDownloader)
                        ?: return CompletableFuture.completedFuture(false)

    val sdkDownloadedFuture = CompletableFuture<Boolean>()

    coroutineScope.launch(Dispatchers.EDT) {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdkDownloadTask.suggestedSdkName)) {
        val (selectedFile, error) = JdkInstaller.getInstance().validateInstallDir(sdkDownloadTask.request.installDir.toString())
        if (selectedFile == null) {
          Messages.showErrorDialog(project, JavaUiBundle.message("jdk.download.error.message", error), JavaUiBundle.message("jdk.download.error.title"))
          sdkDownloadedFuture.complete(false)
          return@withBackgroundProgress
        }
        val downloadTask = jdkDownloader.prepareDownloadTask(project, sdkDownloadTask.jdkItem, selectedFile)
        if (downloadTask == null) {
          // The error was handled inside the JdkDownloader.prepareDownloadTask function
          sdkDownloadedFuture.complete(false)
          return@withBackgroundProgress
        }
        scheduleSetupInstallableSdk(project, downloadTask, sdkDownloadedFuture, onInstalledSdk)
      }
    }

    return sdkDownloadedFuture
  }

  fun scheduleDownloadJdkForNewProject(sdkDownloadTask: JdkDownloadTask): CompletableFuture<Boolean> {
    return scheduleDownloadJdk(sdkDownloadTask) {
      ProjectRootManager.getInstance(project).projectSdk = it
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
}