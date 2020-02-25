// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.CodeInsightUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.newImpl.structures.CodeFragment
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import java.lang.RuntimeException

data class PsiReplace(val source: PsiElement, val target: PsiElement)

class BodyBuilder(private val factory: PsiElementFactory) {

  private fun expressionOf(expression: String) = factory.createExpressionFromText(expression, null)

  private fun statementOf(statement: String) = factory.createStatementFromText(statement, null)

  private fun findExitSubstitutions(flowOutput: FlowOutput, dataOutput: DataOutput): List<PsiReplace> {
    val replacement = when (flowOutput) {
      is ConditionalFlow -> when (dataOutput) {
        is VariableOutput -> "return null;"
        ArtificialBooleanOutput -> "return true;"
        is ExpressionOutput -> "return null;"
        is EmptyOutput -> throw IllegalArgumentException()
      }
      is UnconditionalFlow -> when (dataOutput) {
        is VariableOutput, EmptyOutput -> "return;"
        is ExpressionOutput -> null
        ArtificialBooleanOutput -> throw IllegalArgumentException()
      }
      is EmptyFlow -> null
    }
    return when {
      replacement != null -> flowOutput.statements.map { statement -> PsiReplace(statement, statementOf(replacement)) }
      else -> emptyList()
    }
  }

  private fun createMissedDeclarations(missedDeclarations: List<PsiVariable>): List<PsiDeclarationStatement> {
    return missedDeclarations.map { variable ->
      factory.createVariableDeclarationStatement(requireNotNull(variable.name), variable.type, null)
    }
  }

  private fun createInputReplacements(inputGroup: InputParameter): List<PsiReplace> {
    return inputGroup.references.map { reference -> PsiReplace(reference, expressionOf(inputGroup.name)) }
  }

  private fun getDefaultReturn(dataOutput: DataOutput, flowOutput: FlowOutput): PsiStatement? {
    return when(dataOutput) {
      is VariableOutput -> statementOf("return ${dataOutput.name};")
      is ExpressionOutput -> if (flowOutput is ConditionalFlow) statementOf("return null;") else null
      ArtificialBooleanOutput -> statementOf("return false;")
      EmptyOutput -> null
    }
  }

  private fun buildBodyForExpression(inputParameters: List<InputParameter>, dataOutput: ExpressionOutput): PsiCodeBlock {
    val expression = dataOutput.returnExpressions.single()
    val normalizedExpression = PsiUtil.skipParenthesizedExprDown(expression)!!
    val endStatement = when (dataOutput.type) {
      PsiType.VOID -> factory.createStatementFromText("${normalizedExpression.text};", null)
      else -> factory.createStatementFromText("return ${normalizedExpression.text};", null)
    }
    val copyExpression = when (endStatement) {
      is PsiReturnStatement -> endStatement.returnValue!!
      is PsiExpressionStatement -> endStatement.expression
      else -> throw RuntimeException()
    }
    fun <T: PsiElement> findInCopy(element: T): T {
      val sourceStartOffset: Int = normalizedExpression.textRange.startOffset
      val copyStartOffset: Int = copyExpression.textRange.startOffset
      val range = element.textRange.shiftRight(copyStartOffset - sourceStartOffset)
      return CodeInsightUtil.findElementInRange(copyExpression.containingFile, range.startOffset, range.endOffset, element.javaClass)
    }
    val inCopyParameters = inputParameters.map { parameter ->
      InputParameter(
        references = parameter.references.map (::findInCopy),
        name = parameter.name,
        type = parameter.type
      )
    }
    val replacements = inCopyParameters.map { createInputReplacements(it) }.flatten()
    replacements.forEach { replacement -> replacement.source.replace(replacement.target) }

    val codeBlock = factory.createCodeBlock()
    codeBlock.add(endStatement)
    return codeBlock
  }

  fun build(elements: List<PsiElement>,
            flowOutput: FlowOutput,
            dataOutput: DataOutput,
            inputParameters: List<InputParameter>,
            missedDeclarations: List<PsiVariable>): PsiCodeBlock {

    val expression = elements.singleOrNull() as? PsiExpression
    if (expression != null && dataOutput is ExpressionOutput) return buildBodyForExpression(inputParameters, dataOutput)

    val fragment = CodeFragment.of(elements)
    val copy = CodeFragment.copyOf(fragment)
    val exitCopies = flowOutput.statements.map { statement -> CodeFragment.findSameElementInCopy(fragment, copy, statement) }
    val inCopyFlowOutput = when (flowOutput) {
      is ConditionalFlow -> ConditionalFlow(exitCopies)
      is UnconditionalFlow -> UnconditionalFlow(exitCopies, flowOutput.isDefaultExit)
      EmptyFlow -> EmptyFlow
    }
    val inCopyInputGroups = inputParameters.map { group ->
      InputParameter(
        references = group.references.map { statement -> CodeFragment.findSameElementInCopy(fragment, copy, statement) },
        name = group.name,
        type = group.type
      )
    }
    val exitSubstitution = findExitSubstitutions(inCopyFlowOutput, dataOutput)
    val defaultReturn = getDefaultReturn(dataOutput, flowOutput)
    val inputReplacements = inCopyInputGroups.map { createInputReplacements(it) }.flatten()
    val requiredDeclarations = createMissedDeclarations(missedDeclarations)

    (inputReplacements + exitSubstitution).forEach { (source, target) -> source.replace(target) }
    if (defaultReturn != null) {
      copy.commonParent.addAfter(defaultReturn, copy.lastElement)
    }
    requiredDeclarations.forEach { declaration -> copy.commonParent.addBefore(declaration, copy.firstElement) }
    return copy.commonParent as PsiCodeBlock
  }
}