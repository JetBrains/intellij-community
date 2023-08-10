// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

object ParameterObjectUtils {

  fun createDeclaration(introducedClass: PsiClass): PsiDeclarationStatement {
    val factory = PsiElementFactory.getInstance(introducedClass.project)
    val typeParameters = introducedClass.typeParameters.map(factory::createType).toTypedArray()
    val type = factory.createType(introducedClass, *typeParameters)
    val typeElement = if (HighlightingFeature.DIAMOND_TYPES.isAvailable(introducedClass) && typeParameters.isNotEmpty()) {
      "${type.name}<>"
    } else {
      type.canonicalText
    }
    val constructor = introducedClass.constructors.first()
    val parameters = constructor.parameterList.parameters.joinToString(separator = ",") { it.name }
    val expression = factory.createExpressionFromText("new $typeElement($parameters)", introducedClass)
    return factory.createVariableDeclarationStatement("result", type, expression)
  }

  fun findAffectedReferences(variables: List<PsiVariable>, scope: List<PsiElement>): List<PsiReferenceExpression>? {
    return  variables.flatMap { findAffectedReferences(it, scope) ?: return null }
  }

  private fun findAffectedReferences(variable: PsiVariable, scope: List<PsiElement>): List<PsiReferenceExpression>? {
    val startingOffset = scope.last().textRange.endOffset
    val references = ReferencesSearch.search(variable)
      .mapNotNull { it.element as? PsiReferenceExpression }
      .filter { reference -> reference.textRange.startOffset >= startingOffset }
      .sortedBy { reference -> reference.textRange.startOffset }
    val firstAssignment = references.find { reference -> PsiUtil.isAccessedForWriting(reference) } ?: return references
    val assignmentExpression = PsiTreeUtil.getParentOfType(firstAssignment, PsiAssignmentExpression::class.java)
    if (assignmentExpression == null) return null
    if (assignmentExpression.parent.parent != PsiTreeUtil.findCommonParent(assignmentExpression, scope.last())) return null
    return references.filter { reference -> reference.textRange.endOffset <= assignmentExpression.textRange.endOffset } - firstAssignment
  }

}