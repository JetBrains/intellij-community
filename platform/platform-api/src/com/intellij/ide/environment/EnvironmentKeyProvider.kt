// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.environment

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Used for registration of environment keys.
 */
@ApiStatus.Experimental
interface EnvironmentKeyProvider {

  companion object {
    @JvmStatic
    val EP_NAME : ExtensionPointName<EnvironmentKeyProvider> = ExtensionPointName("com.intellij.environmentKeyProvider")
  }

  /**
   * Returns all keys that are used by a client of [EnvironmentService].
   * Each [EnvironmentKey] must be registered at least in one [EnvironmentKeyProvider].
   */
  fun getAllKeys(): List<EnvironmentKey>

  /**
   * Returns all keys that are absolutely required for a project to be configured without interaction with the user.
   */
  suspend fun getRequiredKeys(project: Project) : List<EnvironmentKey>
}