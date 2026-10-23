// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
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
interface InlayHintSettingsOverrider {

  /**
   * The result overrides the existing settings for the inlay hint.
   *
   * Possible options:
   * - `null` - defers to the platform or to other overriders
   * - `false` - disables the inlay hint
   * - `true` - enables the inlay hint
   */
  fun isEnabled(key: InlayHintSettingsKey): Boolean?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<InlayHintSettingsOverrider> =
      ExtensionPointName.create("com.intellij.codeInsight.inlayHintSettingsOverrider")

    @JvmStatic fun isActive(): Boolean = EP_NAME.hasAnyExtensions()

    /**
     * Combines the results of all overriders.
     *
     * - `false` if at least one overrider disables the inlay hint.
     * - `true` if no overrider disables the inlay hint and at least one enables it.
     * - `null` if no overrider has a result for the inlay hint.
     */
    @JvmStatic fun isEnabledByOverriders(key: InlayHintSettingsKey): Boolean? {
      val results = EP_NAME.extensionList.map { it.isEnabled(key) }
      if (results.contains(false)) return false
      if (results.contains(true)) return true
      return null
    }
  }
}
