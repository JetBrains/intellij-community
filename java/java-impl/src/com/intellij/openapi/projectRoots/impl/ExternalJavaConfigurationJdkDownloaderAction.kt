// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.jps.model.java.JdkVersionDetector
import javax.swing.JComponent

/**
 * Provides an action to copy the external tool download command to the clipboard.
 */
public class ExternalJavaConfigurationJdkDownloaderAction : ExternalJavaConfigurationMissingAction {
  override fun <T : JdkReleaseData> createAction(
    project: Project,
    provider: ExternalJavaConfigurationProvider<T>,
    releaseData: T,
  ): AnAction? {
    val downloadExtension =
      SdkDownload.EP_NAME.findFirstSafe { it: SdkDownload -> it.supportsDownload(JavaSdk.getInstance()) } ?: return null
    val action = MyAction(downloadExtension, provider, releaseData)
    return action
  }

  private class MyAction<T: JdkReleaseData>(val downloadExtension: SdkDownload, val provider: ExternalJavaConfigurationProvider<T>, val releaseData: T) :
    AnAction(JavaBundle.message("external.java.configuration.download.in.ide"),
             null, AllIcons.Actions.Download) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      if (releaseData.variant == JdkVersionDetector.Variant.Unknown) {
        e.presentation.isEnabled = false
        e.presentation.text = JavaBundle.message("external.java.configuration.download.in.ide.unavailable")
      } else {
        e.presentation.isEnabled = true
        e.presentation.text = JavaBundle.message("external.java.configuration.download.in.ide")
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      val component = e.inputEvent?.component as? JComponent ?: return

      project.service<ExternalJavaConfigurationService>().scope.launch {
        val task = withContext(Dispatchers.EDT) {
          downloadExtension.pickSdk(
            JavaSdk.getInstance(), ProjectSdksModel(),
            component, null
          ) { item -> releaseData.matchAgainstItem(item) != ReleaseDataMatching.NO_MATCH }
        } ?: return@launch

        val sdk = JdkDownloadUtil.createDownloadSdk(JavaSdk.getInstance(), task)

        writeAction {
          ProjectRootManager.getInstance(project).projectSdk = sdk
        }

        project.service<ExternalJavaConfigurationService>().updateFromConfig(provider, false)
        JdkDownloadUtil.downloadSdk(sdk)
      }
    }
  }
}
