// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

internal val PARAMETER_HINTS_SUPPRESSORS_EP = ExtensionPointName.create<ParameterNameHintsSuppressor>("com.intellij.codeInsight.parameterNameHintsSuppressor")

@ApiStatus.Experimental
interface ParameterNameHintsSuppressor {
  fun isSuppressedFor(file: PsiFile, inlayInfo: InlayInfo): Boolean

  companion object All {
    fun isSuppressedFor(file: PsiFile, inlayInfo: InlayInfo): Boolean =
      PARAMETER_HINTS_SUPPRESSORS_EP.extensions().anyMatch { it.isSuppressedFor(file, inlayInfo) }
  }
}