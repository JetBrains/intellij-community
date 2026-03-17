// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.parentOfType

sealed interface ResultObjectBuilder {
  fun createClass(): PsiClass
  fun createDeclaration(): PsiDeclarationStatement
  fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression
  fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression?

  companion object {
    private const val DEFAULT_OBJECT_NAME = "Result"

    internal fun findSafeName(context: PsiElement): String {
      val clazz = context.parentOfType<PsiClass>() ?: return DEFAULT_OBJECT_NAME
      val usedNames = clazz.allInnerClasses.map { it.name }.toSet() // Probably name?

      val nameSequence = sequenceOf(DEFAULT_OBJECT_NAME) + generateSequence(1) { seed -> seed + 1 }.map { number -> "$DEFAULT_OBJECT_NAME$number" }
      return nameSequence.first { name -> name !in usedNames }
    }
  }
}