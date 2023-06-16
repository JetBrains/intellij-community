// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiReferenceExpression

interface ResultObjectBuilder {
  fun createClass(): PsiClass
  fun createDeclaration(): PsiDeclarationStatement
  fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression
  fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression?
}