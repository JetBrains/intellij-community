// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.TypeUtils

object ParameterObjectUtils {

  fun createDeclaration(introducedClass: PsiClass): PsiDeclarationStatement {
    val parameters = introducedClass.constructors.first().parameterList.parameters.map(PsiParameter::getName).joinToString(separator = ",")
    val initializer = "new ${introducedClass.name}($parameters)"
    val factory = PsiElementFactory.getInstance(introducedClass.project)
    val expression = factory.createExpressionFromText(initializer, introducedClass)
    return factory.createVariableDeclarationStatement("result", TypeUtils.getType(introducedClass), expression)
  }

  fun findAffectedReferences(variables: List<PsiVariable>, startingElement: PsiElement?): List<PsiReferenceExpression> {
    val startingPoint = startingElement?.textRange?.startOffset ?: return emptyList()
    return  variables.flatMap { findAffectedReferences(it, startingPoint) }
  }

  private fun findAffectedReferences(variable: PsiVariable, startingOffset: Int): List<PsiReferenceExpression> {
    val references = ReferencesSearch.search(variable)
      .mapNotNull { it.element as? PsiReferenceExpression }
      .filter { reference -> reference.textRange.startOffset >= startingOffset }
      .sortedBy { reference -> reference.textRange.startOffset }
    val firstAssignment = references.find { reference -> PsiUtil.isAccessedForWriting(reference) }
    val endPoint = PsiTreeUtil.getParentOfType(firstAssignment, PsiAssignmentExpression::class.java)?.textRange?.endOffset
    return if (firstAssignment != null && endPoint != null) {
      references.filter { reference -> reference.textRange.endOffset <= endPoint } - firstAssignment
    }
    else {
      references
    }
  }

}