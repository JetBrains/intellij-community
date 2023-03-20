// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle

class ProjectOpenKeyRegistry : EnvironmentKeyRegistry {
  companion object {
    val PROJECT_OPEN_PROCESSOR = EnvironmentKey.createKey("project.open.type", ProjectBundle.messagePointer("project.open.processor.environment.property"))
  }

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = listOf()

  override fun getAllKeys(): List<EnvironmentKey> = listOf(
    PROJECT_OPEN_PROCESSOR,
  )
}
