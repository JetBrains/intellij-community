// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight

import com.intellij.model.Symbol
import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolService
import com.intellij.psi.*
import com.intellij.psi.LambdaUtil.resolveFunctionalInterfaceClass
import com.intellij.psi.util.PsiUtil.isJavaToken
import com.intellij.psi.util.PsiUtil.resolveClassInType

public class JavaImplicitReferenceProvider : ImplicitReferenceProvider {

  override fun resolveAsReference(element: PsiElement): Collection<Symbol> {
    return listOfNotNull(
      doResolveAsReference(element)
        ?.let(PsiSymbolService.getInstance()::asSymbol) // this line will be removed when PsiClass will implement Symbol
    )
  }

  private fun doResolveAsReference(elementAtCaret: PsiElement): PsiClass? {
    val parent = elementAtCaret.parent
    if (parent is PsiFunctionalExpression && (isJavaToken(elementAtCaret, JavaTokenType.ARROW) ||
                                              isJavaToken(elementAtCaret, JavaTokenType.DOUBLE_COLON))) {
      return resolveFunctionalInterfaceClass(parent)
    }
    else if (elementAtCaret is PsiKeyword && parent is PsiTypeElement && parent.isInferredType) {
      return resolveClassInType(parent.type)
    }
    else {
      return null
    }
  }
}
