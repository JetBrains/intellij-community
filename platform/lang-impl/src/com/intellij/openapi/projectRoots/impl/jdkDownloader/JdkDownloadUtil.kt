// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTracker
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.coroutines.resume

@ApiStatus.Internal
object JdkDownloadUtil {

  suspend fun pickJdkItemAndPath(project: Project, filter: (JdkItem) -> Boolean): Pair<JdkItem, Path>? {
    val wsl = project.basePath?.let { WslPath.getDistributionByWindowsUncPath(it) }
    val eel = if (Registry.`is`("java.home.finder.use.eel")) project.getEelDescriptor().upgrade() else null
    val jdkPredicate = when {
      eel != null -> JdkPredicate.forEel(eel)
      wsl != null -> JdkPredicate.forWSL()
      else -> JdkPredicate.default()
    }
    val jdkListDownloader = JdkListDownloader.getInstance()
    val jdkItems = jdkListDownloader.downloadModelForJdkInstaller(null, jdkPredicate)
    val jdkItem = jdkItems.firstOrNull(filter) ?: return null
    val jdkInstaller = JdkInstaller.getInstance()
    val jdkHome = jdkInstaller.defaultInstallDir(jdkItem, eel, wsl)
    return jdkItem to jdkHome
  }

  suspend fun createDownloadTask(project: Project, jdkItem: JdkItem, jdkHome: Path): SdkDownloadTask? {
    val downloadRequest = try {
      JdkInstaller.getInstance().prepareJdkInstallation(jdkItem, jdkHome)
    }
    catch (ex: JdkInstallationException) {
      withContext(Dispatchers.EDT) {
        Messages.showErrorDialog(project,
                                 ProjectBundle.message("error.message.text.jdk.download.failed", ex.reason),
                                 ProjectBundle.message("error.message.title.download.jdk")
        )
      }
      return null
    }

    return JdkDownloadTask(jdkItem, downloadRequest, project)
  }

  suspend fun createDownloadSdk(sdkType: SdkType, sdkDownloadTask: SdkDownloadTask): Sdk {
    return writeAction {
      createDownloadSdkInternal(sdkType, sdkDownloadTask)
    }
  }

  @ApiStatus.Internal
  @RequiresWriteLock
  fun createDownloadSdkInternal(sdkType: SdkType, sdkDownloadTask: SdkDownloadTask): Sdk {
    val sdkTable = ProjectJdkTable.getInstance()
    val sdks = sdkTable.allJdks.asList()
    val sdk = ProjectSdksModel.createDownloadSdkInternal(sdkType, sdkDownloadTask, sdks)
    sdkTable.addJdk(sdk)
    return sdk
  }

  suspend fun downloadSdk(sdk: Sdk): Boolean {
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
}