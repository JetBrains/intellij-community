// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.dependency.analyzer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId

class AnalyzeDependenciesAction : AbstractAnalyzeDependenciesAction() {
  override fun getSystemId(e: AnActionEvent): ProjectSystemId? = e.getData(ExternalSystemDataKeys.EXTERNAL_SYSTEM_ID)

  override fun getExternalProjectPath(e: AnActionEvent): String? = null

  override fun getDependency(e: AnActionEvent): DependencyContributor.Dependency? = null
}