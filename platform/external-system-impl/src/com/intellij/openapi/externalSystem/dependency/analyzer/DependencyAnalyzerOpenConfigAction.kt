// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile

abstract class DependencyAnalyzerOpenConfigAction(val systemId: ProjectSystemId) : DumbAwareAction() {

  abstract fun getConfigFile(e: AnActionEvent): VirtualFile?

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val virtualFile = getConfigFile(e) ?: return
    FileEditorManager.getInstance(project).openFile(virtualFile, true)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      systemId == e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID) &&
      getConfigFile(e) != null
  }

  init {
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.open.action.name", systemId.readableName)
    templatePresentation.description = ExternalSystemBundle.message("external.system.dependency.analyzer.open.action.description", systemId.readableName)
  }
}