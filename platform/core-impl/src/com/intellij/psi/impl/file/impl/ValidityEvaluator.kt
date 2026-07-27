// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractFileViewProvider
import com.intellij.psi.FileViewProvider
import org.jetbrains.annotations.Contract

internal interface ValidityEvaluator {

  @Contract(pure = true)
  fun isRecreatedViewProviderIdentical(
    virtualFile: VirtualFile,
    provider: AbstractFileViewProvider,
    context: CodeInsightContext,
  ): Boolean

  @Contract(pure = false)
  fun evaluateValidity(viewProvider: AbstractFileViewProvider): Boolean

  @Contract(pure = true)
  fun canViewProviderBeResurrected(viewProvider: AbstractFileViewProvider): Boolean

  fun reanimateProviderIfNecessary(vFile: VirtualFile, viewProvider: FileViewProvider?): FileViewProvider?
}