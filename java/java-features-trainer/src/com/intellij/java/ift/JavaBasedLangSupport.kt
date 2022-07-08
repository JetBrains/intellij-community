// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import training.lang.AbstractLangSupport
import training.project.ProjectUtils
import java.nio.file.Path

abstract class JavaBasedLangSupport : AbstractLangSupport() {
  protected val sourcesDirectoryPath = "src"

  override fun installAndOpenLearningProject(contentRoot: Path,
                                             projectToClose: Project?,
                                             postInitCallback: (learnProject: Project) -> Unit) {
    super.installAndOpenLearningProject(contentRoot, projectToClose) { project ->
      JavaProjectUtil.findJavaSdkAsync(project) { sdk ->
        if (sdk != null) {
          applyProjectSdk(sdk, project)
        }
        postInitCallback(project)
      }
    }
  }

  override fun getSdkForProject(project: Project, selectedSdk: Sdk?): Sdk? {
    return null
  }

  override fun applyProjectSdk(sdk: Sdk, project: Project) {
    val applySdkAction = {
      runWriteAction { JavaSdkUtil.applyJdkToProject(project, sdk) }
    }
    runInEdt {
      CommandProcessor.getInstance().executeCommand(project, applySdkAction, null, null)
    }
  }

  override fun applyToProjectAfterConfigure(): (Project) -> Unit = { newProject ->
    invokeLater { ProjectUtils.markDirectoryAsSourcesRoot(newProject, sourcesDirectoryPath) }
  }

  override fun checkSdk(sdk: Sdk?, project: Project) {}

  override fun blockProjectFileModification(project: Project, file: VirtualFile): Boolean = true

  override fun isSdkConfigured(project: Project) = ProjectRootManager.getInstance(project).projectSdk?.sdkType is JavaSdkType
}