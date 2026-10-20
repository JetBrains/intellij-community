// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface InlayHintSettingsKey

@ApiStatus.Internal
data class LegacyInlayHintSettingsKey(
  val language: Language?,
  val providerId: String
) : InlayHintSettingsKey

@ApiStatus.Internal
data class DeclarativeInlayHintSettingsKey(
  val providerId: String,
  val optionId: String?
) : InlayHintSettingsKey

@ApiStatus.Internal
data class CodeVisionInlayHintSettingsKey(val groupId: String) : InlayHintSettingsKey

@ApiStatus.Internal
data class ParameterHintSettingsKey(
  val language: Language,
  val optionId: String?
) : InlayHintSettingsKey

@ApiStatus.Internal
data class InlayHintSettingsRequest(
  val project: Project,
  val editor: Editor?,
  val settingsKey: InlayHintSettingsKey
)

@ApiStatus.Internal
interface InlayHintSettingsOverrider {

  /**
   * The result overrides the existing settings for the inlay hint.
   *
   * Possible options:
   * - `null` - when there is no option defined, the caller uses the platform setting
   * - `false` - disables the inlay hint
   * - `true` - enables the inlay hint
   *
   * The overrider cannot enable a hint which was disabled in the platform settings
   */
  fun isEnabled(request: InlayHintSettingsRequest): Boolean?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<InlayHintSettingsOverrider> =
      ExtensionPointName.create("com.intellij.codeInsight.inlayHintSettingsOverrider")

    @JvmStatic fun isActive(): Boolean = EP_NAME.hasAnyExtensions()

    @JvmStatic fun isEnabledByOverriders(request: InlayHintSettingsRequest): Boolean? {
      val results = EP_NAME.extensionList.map { it.isEnabled(request) }
      if (results.contains(false)) return false
      if (results.contains(true)) return true
      return null
    }
  }
}
