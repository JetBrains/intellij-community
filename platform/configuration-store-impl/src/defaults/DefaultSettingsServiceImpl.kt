// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.defaults

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Restores the default settings on the calling side
 */
@ApiStatus.Internal
class DefaultSettingsServiceImpl : DefaultSettingsService {

  override fun restoreDefaultSettings(project: Project?) {
    DefaultSettingsHelper.restoreDefaultSettings(project)
  }

}
