// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.pom.Navigatable

abstract class DependencyAnalyzerGoToAction(val systemId: ProjectSystemId) : DumbAwareAction() {

  abstract fun getNavigatable(e: AnActionEvent): Navigatable?

  override fun actionPerformed(e: AnActionEvent) {
    val navigatable = getNavigatable(e) ?: return
    if (navigatable.canNavigate()) {
      navigatable.navigate(true)
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible =
      systemId == e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID) &&
      getNavigatable(e) != null
  }

  init {
    templatePresentation.text = ExternalSystemBundle.message("external.system.dependency.analyzer.go.to.action.name", systemId.readableName)
    templatePresentation.description = ExternalSystemBundle.message("external.system.dependency.analyzer.go.to.action.description", systemId.readableName)
  }
}