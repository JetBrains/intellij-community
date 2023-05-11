// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle

class ProjectOpenKeyProvider : EnvironmentKeyProvider {
  companion object {
    val PROJECT_OPEN_PROCESSOR: EnvironmentKey = EnvironmentKey.create("project.open.type")
  }

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = listOf()

  override fun getKnownKeys(): Map<EnvironmentKey, String> = mapOf(
    PROJECT_OPEN_PROCESSOR to ProjectBundle.message("project.open.processor.environment.property"),
  )
}
