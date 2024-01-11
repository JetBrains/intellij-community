// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloadTask
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkDownloader
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkInstaller
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JdkDownloadService(private val project: Project, private val coroutineScope: CoroutineScope) {
  private fun doJdkDownload(project: Project, downloadTask: SdkDownloadTask, isCreatingNewProject: Boolean, module: Module) {
    val model = ProjectStructureConfigurable.getInstance(project).projectJdksModel
    coroutineScope.launch(Dispatchers.EDT) {
      writeAction {
        model.setupInstallableSdk(JavaSdkImpl.getInstance(), downloadTask) { sdk ->
          ProjectJdkTable.getInstance().addJdk(sdk)
          if (isCreatingNewProject) {
            ProjectRootManager.getInstance(project).projectSdk = sdk
          } else {
            ModuleRootManager.getInstance(module).modifiableModel.apply {
              this.sdk = sdk
            }.commit()
          }
        }
      }
    }
  }

  fun downloadJdk(sdkDownloadTask: JdkDownloadTask, module: Module, isCreatingNewProject: Boolean) {
    val jdkDownloader = (SdkDownload.EP_NAME.findFirstSafe { it is JdkDownloader } as? JdkDownloader) ?: return
    coroutineScope.launch(Dispatchers.EDT) {
      withBackgroundProgress(project, JavaUiBundle.message("progress.title.downloading", sdkDownloadTask.suggestedSdkName)) {
        val (selectedFile) = JdkInstaller.getInstance().validateInstallDir(sdkDownloadTask.plannedHomeDir)
        if (selectedFile != null) {
          jdkDownloader.prepareDownloadTask(project, sdkDownloadTask.jdkItem, selectedFile) { downloadTask ->
            doJdkDownload(project, downloadTask, isCreatingNewProject, module)
          }
        } else {
          Messages.showErrorDialog(project, JavaUiBundle.message("jdk.download.error.message"), JavaUiBundle.message("jdk.download.error.title"))
        }
      }
    }
  }
}