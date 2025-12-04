// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Extension allowing customization of defaults for [CodeVisionSettings].
 */
@Experimental
interface CodeVisionSettingsDefaults {
  companion object {
    private val EP_NAME = ExtensionPointName<CodeVisionSettingsDefaults>("com.intellij.codeInsight.codeVision.settings.defaults")

    fun getInstance(): CodeVisionSettingsDefaults = EP_NAME.extensionList.firstOrNull() ?: NONE
  }

  /**
   * Specified the default enabled state for each given provider. Any providers not specified are assumed to be
   * enabled by default.
   */
  val defaultEnablementForProviderId: Map<String, Boolean>
    get() = emptyMap()

  val defaultPosition: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Top

  private object NONE : CodeVisionSettingsDefaults
}
