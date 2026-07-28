// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("SdkDownloadTrackerKt")
package com.intellij.openapi.roots.ui.configuration.projectRoot

import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Consumer

private val LOG = logger<SdkDownloadTracker>()

@Service(Service.Level.APP)
private class SdkDownloadTrackerService(val scope: CoroutineScope)

internal fun runSdkDownloadTask(
  title: @NlsContexts.ProgressTitle String,
  downloadAction: Consumer<ProgressIndicator>,
): Job {
  return service<SdkDownloadTrackerService>().scope.launch(Dispatchers.IO) {
    withBackgroundProgress(ProjectManager.getInstance().defaultProject, title, cancellable = true) {
      coroutineToIndicator { indicator ->
        downloadAction.accept(indicator)
      }
    }
  }
}

private data class SdkInfo(val sdk: Sdk, val actualVersion: String?)

@RequiresBackgroundThread
internal fun runCompleteSdkDownload(sdks: List<Sdk>, task: SdkDownloadTask): Job {
  return service<SdkDownloadTrackerService>().scope.launch {

    val sdkInfos = withContext(Dispatchers.IO) {
      sdks.map { sdk ->
        val sdkType = sdk.sdkType as SdkType
        val actualVersion = try {
          sdkType.getVersionString(sdk)
        }
        catch (e: Exception) {
          LOG.warn("Failed to get version string for downloaded SDK $sdk. ${e.message}", e)
          null
        }

        try {
          backgroundWriteAction {
            sdkType.setupSdkPaths(sdk)
          }
        }
        catch (e: Exception) {
          LOG.warn("Failed to set up SDK paths for $sdk. ${e.message}", e)
        }

        SdkInfo(sdk, actualVersion)
      }
    }

    for ((sdk, actualVersion) in sdkInfos) {
      try {
        backgroundWriteAction {
          SdkDownloadTracker.getInstance().configureSdk(sdk, task)

          if (actualVersion != null) {
            sdk.sdkModificator.apply {
              versionString = actualVersion
              commitChanges()
            }
          }
        }

        for (project in ProjectManager.getInstance().openProjects) {
          val rootManager = ProjectRootManagerImpl.getInstanceImpl(project)
          val projectSdk = rootManager.projectSdk
          if (projectSdk != null && projectSdk.name == sdk.name) {
            rootManager.projectJdkChanged()
          }
        }
      }
      catch (e: Exception) {
        LOG.warn("Failed to setup SDK $sdk. ${e.message}", e)
      }
    }
  }
}
