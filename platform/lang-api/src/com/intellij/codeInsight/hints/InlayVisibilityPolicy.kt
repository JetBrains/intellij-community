// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface InlayVisibilityKey

@ApiStatus.Internal
data class LegacyInlayHintVisibilityKey(
  val language: Language?,
  val providerId: String
) : InlayVisibilityKey

@ApiStatus.Internal
data class DeclarativeInlayHintVisibilityKey(
  val providerId: String,
  val optionId: String?
) : InlayVisibilityKey

@ApiStatus.Internal
data class CodeVisionInlayVisibilityKey(val groupId: String) : InlayVisibilityKey

@ApiStatus.Internal
data class ParameterHintsInlayVisibilityKey(
  val language: Language,
  val optionId: String?
) : InlayVisibilityKey

@ApiStatus.Internal
data class InlayVisibilityRequest(
  val project: Project,
  val editor: Editor?,
  val visibilityKey: InlayVisibilityKey
)

@ApiStatus.Internal
interface InlayVisibilityPolicy {

  /**
   * Possible options:
   * - `null` - when there is no option defined, the caller uses the platform setting
   * - `false` - hides the inlay
   * - `true` - the inlay can be shown
   */
  fun accepts(request: InlayVisibilityRequest): Boolean?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<InlayVisibilityPolicy> =
      ExtensionPointName.create("com.intellij.codeInsight.inlayVisibilityPolicy")

    @JvmStatic fun isActive(): Boolean = EP_NAME.hasAnyExtensions()

    @JvmStatic fun isVisible(request: InlayVisibilityRequest): Boolean? {
      val results = EP_NAME.extensionList.map { it.accepts(request) }
      if (results.contains(false)) return false
      if (results.contains(true)) return true
      return null
    }
  }
}
