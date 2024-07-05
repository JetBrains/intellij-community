// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing

import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Internal
class ExternalSystemKeyProvider : EnvironmentKeyProvider {
  object Keys {
    val LINK_UNLINKED_PROJECT : EnvironmentKey = EnvironmentKey.create("external.system.link.unlinked.projects")
  }

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> {
    return listOf()
  }

  override val knownKeys: Map<EnvironmentKey, Supplier<String>> = mapOf(
    Keys.LINK_UNLINKED_PROJECT to ExternalSystemBundle.messagePointer("environment.key.description.link.unlinked.project")
  )
}