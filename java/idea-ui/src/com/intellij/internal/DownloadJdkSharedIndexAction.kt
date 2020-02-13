// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.ui.Messages
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.compute
import java.nio.file.Path

class DownloadJdkSharedIndexAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val type = JavaSdk.getInstance()

    for (sdk in ProjectJdkTable.getInstance().allJdks) {
      if (sdk.sdkType != type) continue
      val promise = downloadIndexForJdk(project, sdk)

      promise.onError {
        invokeLater {
          Messages.showMessageDialog(
            project,
            "Failed to download Shared Index for $sdk. ${it.message}",
            "Shared Indexes", AllIcons.Ide.FatalError
          )
        }
      }

      promise.onSuccess {
        invokeLater {
          Messages.showMessageDialog(
            project,
            "Shared Index for $sdk is downloaded to $it",
            "Shared Indexes", AllIcons.Ide.HectorOn
          )
        }
      }
    }
  }

  private fun downloadIndexForJdk(project: Project, sdk: Sdk): Promise<Path?> {
    val promise = AsyncPromise<Path?>()
    ProgressManager.getInstance().run(object : Task.Backgroundable(project,
                                                                   "Looking for Shared Indexes",
                                                                   true,
                                                                   PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        promise.compute {
          val type = JavaSdk.getInstance() as JavaSdkImpl
          val sdkHash = type.computeJdkFingerprint(sdk) ?: error("Failed to compute hash for $sdk!")
          val request = SharedIndexRequest("jdk", sdkHash)

          val info = SharedIndexesLoader.getInstance().lookupSharedIndex(request, indicator) ?: return@compute null
          val version = info.version

          val targetFile = SharedIndexesLoader.getInstance().selectIndexFileDestination(info, version)
          SharedIndexesLoader.getInstance().downloadSharedIndex(info, indicator, targetFile)
          targetFile.toPath()
        }
      }
    })

    return promise
  }
}
