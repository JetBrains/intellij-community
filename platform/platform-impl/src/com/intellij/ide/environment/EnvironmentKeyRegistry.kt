// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.environment

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Used for registration of environment keys.
 */
@ApiStatus.Experimental
interface EnvironmentKeyRegistry {

  companion object {
    @JvmStatic
    val EP_NAME : ExtensionPointName<EnvironmentKeyRegistry> = ExtensionPointName("com.intellij.environmentKeyRegistry")
  }

  /**
   * Returns all keys that are used by a client of [EnvironmentParametersService].
   * Each [EnvironmentKey] must be registered at least in one [EnvironmentKeyRegistry].
   */
  fun getAllKeys(): List<EnvironmentKey>

  /**
   * Returns all keys that are absolutely required for a project to be configured without interaction with the user.
   */
  suspend fun getRequiredKeys(project: Project) : List<EnvironmentKey>



}