// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlaySettingsConfigurableFactory {
  fun create(project: Project): Configurable

  companion object {
    val EP_NAME: ExtensionPointName<InlaySettingsConfigurableFactory> =
      ExtensionPointName("com.intellij.codeInsight.inlaySettingsConfigurableFactory")
  }
}
