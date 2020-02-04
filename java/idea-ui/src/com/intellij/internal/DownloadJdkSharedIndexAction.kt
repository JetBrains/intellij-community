// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.ui.Messages
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
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
    //TODO: better of the UI thread!
    val type = JavaSdk.getInstance() as JavaSdkImpl
    val sdkHash = type.computeJdkFingerprint(sdk) ?: return rejectedPromise("Failed to compute hash for $sdk!")
    return SharedIndexesLoader.getInstance().lookupIndexes(project, SharedIndexRequest("jdk", sdkHash))
  }
}