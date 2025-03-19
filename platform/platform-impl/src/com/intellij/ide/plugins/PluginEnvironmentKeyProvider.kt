// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.environment.EnvironmentKey
import com.intellij.ide.environment.EnvironmentKeyProvider
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Internal
class PluginEnvironmentKeyProvider : EnvironmentKeyProvider {

  object Keys {
    val ENABLE_DISABLED_DEPENDENT_PLUGINS = EnvironmentKey.create("enable.disabled.dependent.plugins")
  }

  override val knownKeys: Map<EnvironmentKey, Supplier<String>> = mapOf(
    Keys.ENABLE_DISABLED_DEPENDENT_PLUGINS to IdeBundle.messagePointer("environment.key.description.enable.disabled.dependent.plugins")
  )

  override suspend fun getRequiredKeys(project: Project): List<EnvironmentKey> = emptyList()
}