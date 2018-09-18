// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.PsiElement
import com.intellij.util.Processor

class FallbackSymbolDeclarationSearcher : SymbolDeclarationSearcher {

  override fun processDeclarations(element: PsiElement, offsetInElement: Int, processor: Processor<in SymbolDeclaration>): Boolean {
    val namedElement = TargetElementUtil.getNamedElement(element)
    if (namedElement != null) {
      return processor.process(PsiElementSymbolDeclaration(namedElement))
    }
    else {
      return true
    }
  }
}
