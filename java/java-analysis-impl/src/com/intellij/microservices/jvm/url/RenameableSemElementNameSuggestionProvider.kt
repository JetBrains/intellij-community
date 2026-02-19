// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.jvm.url

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider

internal class RenameableSemElementNameSuggestionProvider : NameSuggestionProvider {
  override fun getSuggestedNames(element: PsiElement, nameSuggestionContext: PsiElement?, result: MutableSet<String>): SuggestedNameInfo? {
    getParameterNameVariants(element)?.map { it.lookupString }?.collectTo(result)
    return null
  }
}