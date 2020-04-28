// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.createDeclaration
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.findInCopy
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.findTopmostParenthesis
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter

data class PsiReplace(val source: PsiElement, val target: PsiElement)

class BodyBuilder(private val factory: PsiElementFactory) {

  private fun expressionOf(expression: String) = factory.createExpressionFromText(expression, null)

  private fun statementOf(statement: String) = factory.createStatementFromText(statement, null)

  private fun findDefaultFlowSubstitution(flowOutput: FlowOutput, dataOutput: DataOutput): String? {
    return when (flowOutput) {
      is ConditionalFlow -> when (dataOutput) {
        is VariableOutput -> "return null;"
        ArtificialBooleanOutput -> "return true;"
        is ExpressionOutput -> "return null;"
        is EmptyOutput -> null
      }
      is UnconditionalFlow -> when (dataOutput) {
        is VariableOutput, is EmptyOutput -> "return;"
        is ExpressionOutput, ArtificialBooleanOutput -> null
      }
      is EmptyFlow -> null
    }
  }

  private fun findExitReplacements(flowOutput: FlowOutput, dataOutput: DataOutput): List<PsiReplace> {
    val replacement = findDefaultFlowSubstitution(flowOutput, dataOutput) ?: return emptyList()
    return flowOutput.statements.map { statement -> PsiReplace(statement, statementOf(replacement)) }
  }

  private fun createInputReplacements(inputGroup: InputParameter): List<PsiReplace> {
    return inputGroup.references
      .map { referenceExpression -> findTopmostParenthesis(referenceExpression) }
      .map { normalizedExpression -> PsiReplace(normalizedExpression, expressionOf(inputGroup.name)) }
  }

  private fun findDefaultReturn(dataOutput: DataOutput, flowOutput: FlowOutput): String? {
    return when (dataOutput) {
      is VariableOutput -> "return ${dataOutput.name};"
      is ExpressionOutput -> if (flowOutput is ConditionalFlow) "return null;" else null
      ArtificialBooleanOutput -> "return false;"
      is EmptyOutput -> null
    }
  }

  private fun createDeclarationForDisabledParameter(parameter: InputParameter): PsiDeclarationStatement {
    val styleManager = CodeStyleManager.getInstance(parameter.references.first().project)
    val declaration = factory.createStatementFromText("int ${parameter.name} = ;", null) as PsiDeclarationStatement
    val variable = declaration.declaredElements.first() as? PsiVariable
    val typeElement = factory.createTypeElement(parameter.type)
    variable?.typeElement?.replace(typeElement)
    return styleManager.reformat(declaration) as PsiDeclarationStatement
  }

  private fun wrapExpression(expression: PsiExpression, shouldBeReturned: Boolean): Pair<PsiStatement, PsiExpression> {
    val statement = if (shouldBeReturned) {
      factory.createStatementFromText("return ${expression.text};", expression.context)
    } else {
      factory.createStatementFromText("${expression.text};", expression.context)
    }
    val block = factory.createCodeBlockFromText("{\n}", expression.context)
    val addedStatement = block.add(statement) as PsiStatement
    val inCopyExpression = when (addedStatement) {
      is PsiReturnStatement -> addedStatement.returnValue!!
      is PsiExpressionStatement -> addedStatement.expression
      else -> throw IllegalStateException()
    }
    return Pair(addedStatement, inCopyExpression)
  }

  private fun findInputParameterInCopy(source: PsiElement, copy: PsiElement, parameter: InputParameter): InputParameter {
    return InputParameter(
      references = parameter.references.map { reference -> findInCopy(source, copy, reference) },
      name = parameter.name,
      type = parameter.type
    )
  }

  fun copyOf(elements: List<PsiElement>): List<PsiElement> {
    val block = factory.createCodeBlockFromText("{}", elements.first().context)
    block.add(PsiParserFacade.SERVICE.getInstance(elements.first().project).createWhiteSpaceFromText("\n    "))
    val first = block.addRange(elements.first(), elements.last())
    val last = block.lastBodyElement!!
    return PsiTreeUtil.getElementsOfRange(first, last)
  }

  fun build(elements: List<PsiElement>,
            flowOutput: FlowOutput,
            dataOutput: DataOutput,
            inputParameters: List<InputParameter>,
            disabledParameters: List<InputParameter>,
            missedDeclarations: List<PsiVariable>): PsiCodeBlock {

    val project = elements.first().project

    val expression = elements.singleOrNull() as? PsiExpression
    val normalizedExpression = PsiUtil.skipParenthesizedExprDown(expression)
    if (normalizedExpression != null) {
      require(dataOutput is ExpressionOutput)
      val (wrappedStatement, wrappedExpression) = wrapExpression(normalizedExpression, dataOutput.type != PsiType.VOID)
      val wrappedInputParameters = inputParameters.map { parameter -> findInputParameterInCopy(normalizedExpression, wrappedExpression, parameter) }
      val wrappedFlowOutput = UnconditionalFlow(listOf(wrappedStatement), true)
      val wrappedDataOutput = dataOutput.copy(returnExpressions = listOf(wrappedExpression))
      return build(listOf(wrappedStatement), wrappedFlowOutput, wrappedDataOutput, wrappedInputParameters, disabledParameters, missedDeclarations)
    }

    val blockStatement = elements.singleOrNull() as? PsiBlockStatement
    val firstElement = blockStatement?.codeBlock?.firstBodyElement ?: elements.first()
    val lastElement = blockStatement?.codeBlock?.lastBodyElement ?: elements.last()
    val normalizedElements = PsiTreeUtil.getElementsOfRange(firstElement, lastElement)
      .dropWhile { it is PsiWhiteSpace }
      .dropLastWhile { it is PsiWhiteSpace }

    val copy = copyOf(normalizedElements)

    val exitCopies = flowOutput.statements.map { statement -> findInCopy(normalizedElements.first(), copy.first(), statement) }
    val inCopyFlowOutput = when (flowOutput) {
      is ConditionalFlow -> ConditionalFlow(exitCopies)
      is UnconditionalFlow -> UnconditionalFlow(exitCopies, flowOutput.isDefaultExit)
      EmptyFlow -> EmptyFlow
    }
    val inCopyInputGroups = inputParameters.map { parameter -> findInputParameterInCopy(normalizedElements.first(), copy.first(), parameter) }
    val exitSubstitution = findExitReplacements(inCopyFlowOutput, dataOutput)
    val inputReplacements = inCopyInputGroups.map { createInputReplacements(it) }.flatten()
    val requiredDeclarations = missedDeclarations.map { createDeclaration(it) }

    (inputReplacements + exitSubstitution).forEach { (source, target) -> source.replace(target) }

    val block = copy.first().parent as PsiCodeBlock

    val defaultReturn = findDefaultReturn(dataOutput, flowOutput)
    if (defaultReturn != null) {
      block.addAfter(statementOf(defaultReturn), copy.last())
    }
    val disabledDeclarations = disabledParameters.map { createDeclarationForDisabledParameter(it) }
    disabledDeclarations.reversed().forEach { declaration ->
      block.addBefore(declaration, copy.first())
      val newLine = PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
      block.addBefore(newLine, copy.first())
    }
    requiredDeclarations.forEach { declaration -> block.addBefore(declaration, copy.first()) }
    return block
  }
}