// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import org.jetbrains.annotations.Contract

internal interface ValidityEvaluator {

  @Contract(pure = true)
  /**
   * Recreates a [FileViewProvider] for [virtualFile] and [context] and compares it with [provider].
   *
   * Returns `null` when the provider can be resurrected; otherwise returns the reason it cannot.
   */
  fun getRecreationFailureReason(
    virtualFile: VirtualFile,
    provider: AbstractFileViewProvider,
    context: CodeInsightContext,
  ): String?

  @Contract(pure = false)
  fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean

  @Contract(pure = true)
  fun canViewProviderBeResurrected(viewProvider: AbstractFileViewProvider): Boolean

  fun reanimateProviderIfNecessary(vFile: VirtualFile, viewProvider: FileViewProvider?): FileViewProvider?
}
