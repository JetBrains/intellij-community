// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

abstract class ExternalSystemDependencyAnalyzerOpenConfigAction(systemId: ProjectSystemId) : DependencyAnalyzerOpenConfigAction(systemId) {

  abstract fun getExternalProjectPath(e: AnActionEvent): String?

  override fun getConfigFile(e: AnActionEvent): VirtualFile? {
    val externalProjectPath = getExternalProjectPath(e) ?: return null

    val fileSystem = LocalFileSystem.getInstance()
    val externalProjectDirectory = fileSystem.refreshAndFindFileByPath(externalProjectPath) ?: return null
    val locator = ExternalSystemConfigLocator.EP_NAME.findFirstSafe { it.targetExternalSystemId == systemId } ?: return null
    val externalSystemConfigPath = locator.adjust(externalProjectDirectory) ?: return null
    return if (externalSystemConfigPath.isDirectory) null else externalSystemConfigPath
  }
}