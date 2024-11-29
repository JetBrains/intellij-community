// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.commands.api

import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface CommandCompletionFactory {
  fun suffix(): Char = '.'
  fun filterSuffix(): Char? = '.'
  fun commandProviders(): List<CommandProvider>
  fun isApplicable(psiFile: PsiFile, offset: Int): Boolean
}