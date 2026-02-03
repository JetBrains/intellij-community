// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.defaults

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Provides an implementation of restoring the default settings
 */
@ApiStatus.Internal
interface DefaultSettingsService {

  companion object {
    fun getInstance(): DefaultSettingsService = service()
  }

  /**
   * Restores the default settings
   *
   * @param project the project where the confirmation dialog should be shown
   */
  fun restoreDefaultSettings(project: Project?)

}
