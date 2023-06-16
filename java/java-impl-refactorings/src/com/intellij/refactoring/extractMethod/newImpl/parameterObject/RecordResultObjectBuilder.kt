// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl.parameterObject

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class RecordResultObjectBuilder(private val record: PsiClass): ResultObjectBuilder {

  companion object {

    fun create(variables: List<PsiVariable>): RecordResultObjectBuilder {
      return RecordResultObjectBuilder(createRecord(variables))
    }

    private fun createRecord(variables: List<PsiVariable>): PsiClass {
      require(variables.isNotEmpty())
      val project = variables.first().project
      val factory = PsiElementFactory.getInstance(project)
      val record = factory.createRecord("Result")
      val header = variables.joinToString(separator = ", ") { variable -> "${variable.type.canonicalText} ${variable.name}" }
      record.recordHeader?.replace(factory.createRecordHeaderFromText(header, record))
      return record
    }
  }

  override fun createClass(): PsiClass = record

  override fun createDeclaration(): PsiDeclarationStatement = ParameterObjectUtils.createDeclaration(record)

  override fun createReferenceReplacement(reference: PsiReferenceExpression): PsiExpression {
    return PsiElementFactory.getInstance(reference.project).createExpressionFromText("result.${reference.text}()", reference.context)
  }

  override fun findVariableReferenceInReplacement(replacement: PsiExpression): PsiReferenceExpression? {
    val place = replacement.textRange.startOffset
    return PsiTreeUtil.findElementOfClassAtOffset(replacement.containingFile, place, PsiReferenceExpression::class.java, false)
  }
}