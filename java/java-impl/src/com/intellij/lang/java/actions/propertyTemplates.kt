// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.psi.*

internal const val FIELD_VARIABLE = "FIELD_NAME_VARIABLE"
internal const val SETTER_PARAM_NAME = "SETTER_PARAM_NAME"

internal interface AccessorTemplateData {
  val fieldRef: PsiElement
  val typeElement: PsiTypeElement
  val endElement: PsiElement?
}

internal class GetterTemplateData(
  override val fieldRef: PsiElement,
  override val typeElement: PsiTypeElement,
  override val endElement: PsiElement?
) : AccessorTemplateData

internal class SetterTemplateData(
  override val fieldRef: PsiElement,
  override val typeElement: PsiTypeElement,
  val parameterName: PsiElement,
  val parameterRef: PsiElement,
  override val endElement: PsiElement?
) : AccessorTemplateData

internal fun PsiMethod.extractGetterTemplateData(): GetterTemplateData {
  val body = requireNotNull(body) { text }
  val returnStatement = body.statements.single() as PsiReturnStatement
  val fieldReference = returnStatement.returnValue as PsiReferenceExpression
  return GetterTemplateData(
    fieldRef = requireNotNull(fieldReference.referenceNameElement) { text },
    typeElement = requireNotNull(returnTypeElement) { text },
    endElement = body.lBrace
  )
}

internal fun PsiMethod.extractSetterTemplateData(): SetterTemplateData {
  val body = requireNotNull(body) { text }
  val assignmentStatement = body.statements.singleOrNull() as? PsiExpressionStatement
  val assignment = requireNotNull(assignmentStatement?.expression as? PsiAssignmentExpression) { text }
  val fieldReference = assignment.lExpression as PsiReferenceExpression
  val parameter = parameterList.parameters.single()
  return SetterTemplateData(
    fieldRef = requireNotNull(fieldReference.referenceNameElement) { text },
    typeElement = requireNotNull(parameter.typeElement) { text },
    parameterName = requireNotNull(parameter.nameIdentifier) { text },
    parameterRef = requireNotNull(assignment.rExpression) { text },
    endElement = body.lBrace
  )
}
