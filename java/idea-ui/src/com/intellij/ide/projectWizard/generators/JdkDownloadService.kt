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
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkItem
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JdkDownloadService(private val project: Project, private val coroutineScope: CoroutineScope) {

  @RequiresEdt
  fun setupInstallableSdk(downloadTask: SdkDownloadTask): Sdk {
    return application.runWriteAction<Sdk> {
      createDownloadSdkInternal(JavaSdk.getInstance(), downloadTask)
    }
  }

  fun downloadSdk(sdk: Sdk) {
    coroutineScope.launch {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdk.name)) {
        downloadSdkInternal(sdk)
      }
    }
  }

  private suspend fun createDownloadTask(jdkItem: JdkItem, jdkHome: Path): SdkDownloadTask? {
    val (selectedFile, error) = JdkInstaller.getInstance().validateInstallDir(jdkHome.toString())
    if (selectedFile == null) {
      withContext(Dispatchers.EDT) {
        Messages.showErrorDialog(project,
                                 JavaUiBundle.message("jdk.download.error.message", error),
                                 JavaUiBundle.message("jdk.download.error.title")
        )
      }
      return null
    }

    val downloadRequest = LOG.runAndLogException {
      JdkInstaller.getInstance().prepareJdkInstallation(jdkItem, selectedFile)
    } ?: return null

    return JdkDownloadTask(jdkItem, downloadRequest, project)
  }

  @RequiresWriteLock
  private fun createDownloadSdkInternal(sdkType: SdkType, sdkDownloadTask: SdkDownloadTask): Sdk {
    val sdkTable = ProjectJdkTable.getInstance()
    val sdks = sdkTable.allJdks.asList()
    val sdk = ProjectSdksModel.createDownloadSdkInternal(sdkType, sdkDownloadTask, sdks)
    sdkTable.addJdk(sdk)
    return sdk
  }

  private suspend fun downloadSdkInternal(sdk: Sdk): Boolean {
    return withContext(Dispatchers.EDT) {
      Disposer.newDisposable("The JdkDownloader#downloadSdk lifecycle").use { disposable ->
        val tracker = SdkDownloadTracker.getInstance()
        tracker.startSdkDownloadIfNeeded(sdk)
        suspendCancellableCoroutine { continuation ->
          val registered = tracker.tryRegisterDownloadingListener(sdk, disposable, null) {
            continuation.resume(it)
          }
          if (!registered) {
            continuation.resume(false)
          }
        }
      }
    }
  }

  fun scheduleDownloadJdk(sdkDownloadTask: JdkDownloadTask, onJdkCreated: suspend (Sdk) -> Unit = {}): CompletableFuture<Boolean> {
    return coroutineScope.async {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdkDownloadTask.suggestedSdkName)) {

        val downloadTask = createDownloadTask(sdkDownloadTask.jdkItem, sdkDownloadTask.request.installDir)
                           ?: return@withBackgroundProgress false

        val sdk = writeAction {
          createDownloadSdkInternal(JavaSdk.getInstance(), downloadTask)
        }

        onJdkCreated(sdk)

        downloadSdkInternal(sdk)
      }
    }.asCompletableFuture()
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

  companion object {
    private val LOG = logger<JdkDownloadService>()
  }
}