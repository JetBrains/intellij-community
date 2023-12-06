// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import java.util.function.Supplier

class ProjectOpenKeyProvider : EnvironmentKeyProvider {
  object Keys {
    val PROJECT_OPEN_PROCESSOR: EnvironmentKey = EnvironmentKey.create("project.open.type")
  }

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = listOf()

  override val knownKeys: Map<EnvironmentKey, Supplier<String>> = mapOf(
    Keys.PROJECT_OPEN_PROCESSOR to ProjectBundle.messagePointer("project.open.processor.environment.property"),
  )
}
