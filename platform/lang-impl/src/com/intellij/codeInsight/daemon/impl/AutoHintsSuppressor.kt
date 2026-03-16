// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

/**
 * Allows disabling automatic hints in the editor (e.g., auto-imports) for a specific file.
 */
@ApiStatus.Internal
interface AutoHintsSuppressor {
  fun areAutoHintsSuppressedFor(psiFile: PsiFile): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName<AutoHintsSuppressor>("com.intellij.autoHintsSuppressor")

    fun areAutoHintsSuppressedFor(psiFile: PsiFile): Boolean {
      return EP_NAME.extensionList.any { it.areAutoHintsSuppressedFor(psiFile) }
    }
  }
}