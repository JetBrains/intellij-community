// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile

internal val PARAMETER_HINTS_SUPPRESSORS_EP: ExtensionPointName<ParameterNameHintsSuppressor> = ExtensionPointName("com.intellij.codeInsight.parameterNameHintsSuppressor")

/**
 * Allows programmatic suppression of parameter hints in specific places.
 *
 * Registered via `com.intellij.codeInsight.parameterNameHintsSuppressor` extension point.
 */
interface ParameterNameHintsSuppressor {
  fun isSuppressedFor(file: PsiFile, inlayInfo: InlayInfo): Boolean

  companion object All {
    fun isSuppressedFor(file: PsiFile, inlayInfo: InlayInfo): Boolean {
      return PARAMETER_HINTS_SUPPRESSORS_EP.extensionList.any { it.isSuppressedFor(file, inlayInfo) }
    }
  }
}