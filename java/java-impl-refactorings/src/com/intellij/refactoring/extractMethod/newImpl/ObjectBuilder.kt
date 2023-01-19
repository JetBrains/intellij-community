// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.siyeh.ig.psiutils.TypeUtils
import com.siyeh.ig.psiutils.VariableAccessUtils

interface ObjectBuilder {
  fun createClass(): PsiClass
  fun createDeclaration(): PsiDeclarationStatement
  fun createReferenceReplacement(reference: PsiExpression): PsiExpression
  fun getAffectedReferences(): List<PsiExpression>

  companion object {
    fun run(variables: List<PsiVariable>, scope: List<PsiElement>){
      require(variables.isNotEmpty())
      require(scope.isNotEmpty())
      val objectBuilder: ObjectBuilder = RecordObjectBuilder.create(variables, scope) ?: return
      val file = scope.first().containingFile
      val rangeMarker = file.viewProvider.document.createRangeMarker(scope.first().textRange.startOffset, scope.last().textRange.endOffset)
      rangeMarker.apply { isGreedyToLeft = true; isGreedyToRight = true; }
      val project = variables.first().project
      WriteCommandAction.writeCommandAction(project).run<Throwable> {
        val references = objectBuilder.getAffectedReferences()
        references.forEach {
          it.replace(objectBuilder.createReferenceReplacement(it))
        }
        val record = objectBuilder.createClass()
        val anchor = PsiTreeUtil.getParentOfType(variables.first(), PsiMethod::class.java)
        anchor?.parent?.addAfter(record, anchor)
        val variableDeclaration = objectBuilder.createDeclaration()
        val last = scope.last()
        last.parent.addAfter(variableDeclaration, last)
      }
      //invokeLater { MethodExtractor().doExtract(file, rangeMarker.textRange) }
    }
  }
}

class RecordObjectBuilder(private val record: PsiClass, private val references: List<PsiExpression>): ObjectBuilder {

  companion object {

    fun create(variables: List<PsiVariable>, scope: List<PsiElement>): RecordObjectBuilder? {
      val record = createRecord(variables)
      val affectedReferences = findAffectedReferences(variables, scope.last().nextSibling)
      return RecordObjectBuilder(record, affectedReferences)
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

    private fun findAffectedReferences(variables: List<PsiVariable>, startingElement: PsiElement?): List<PsiExpression> {
      val startingPoint = startingElement?.textRange?.startOffset ?: return emptyList()
      return  variables.flatMap { findAffectedReferences(it, startingPoint) }
    }

    private fun findAffectedReferences(variable: PsiVariable, startingOffset: Int): List<PsiExpression> {
      val references = ReferencesSearch.search(variable)
        .mapNotNull { it.element as? PsiReferenceExpression }
        .filter { reference -> reference.textRange.startOffset >= startingOffset }
        .sortedBy { reference -> reference.textRange.startOffset }
      val firstAssignment = references.find { reference -> PsiUtil.isAccessedForWriting(reference) }
      val endPoint = PsiTreeUtil.getParentOfType(firstAssignment, PsiAssignmentExpression::class.java)?.textRange?.endOffset
      //ReplaceOperatorAssignmentWithAssignmentIntention
      //ReplacePostfixExpressionWithAssignment
      return if (firstAssignment != null && endPoint != null) {
        references.filter { reference -> reference.textRange.endOffset <= endPoint } - firstAssignment
      }
      else {
        references
      }
    }
  }

  override fun createClass(): PsiClass = record

  override fun createDeclaration(): PsiDeclarationStatement {
    val recordComponents = record.recordComponents
    val parameters = recordComponents.joinToString(separator = ",") { component -> component.name }
    val initializer = "new ${record.name}($parameters)"
    val factory = PsiElementFactory.getInstance(record.project)
    val expression = factory.createExpressionFromText(initializer, record)
    return factory.createVariableDeclarationStatement("result", TypeUtils.getType(record), expression)
  }

  override fun createReferenceReplacement(reference: PsiExpression): PsiExpression {
    return PsiElementFactory.getInstance(reference.project).createExpressionFromText("result.${reference.text}()", reference.context)
  }

  override fun getAffectedReferences(): List<PsiExpression> = references
}

