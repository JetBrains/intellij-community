// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.features

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlineCompletionFeaturesScopeAnalyzer {
  fun analyzeScope(file: PsiFile, offset: Int): ExtendedScope? = null

  companion object EMPTY : InlineCompletionFeaturesScopeAnalyzer

  data class ExtendedScope(
    val scope: Scope?,
    val parentScope: Scope?,
    val grandParentScope: Scope?,
    val prevSiblingScope: Scope?,
    val nextSiblingScope: Scope?,
  )

  data class Scope(
    val type: ScopeType,
    val isScopeInit: Boolean,
    val textRange: TextRange,
  )

  enum class ScopeType {
    Block, // generic scope, use if you don't know which exact scope it is
    File,
    Class,
    Constructor,
    Func, Lambda, Property,
    Call,
    Parameters, Arguments,
    If, While, For, Switch, TryCatch,
    IfPart, ElsePart, ForPart, WhilePart, TryPart, CatchPart, FinallyPart, Case
  }
}