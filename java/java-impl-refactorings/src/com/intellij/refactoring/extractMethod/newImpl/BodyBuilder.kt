// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.daemon.impl.quickfix.AddTypeCastFix
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.createDeclaration
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.getReturnedExpression
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.util.CommonJavaRefactoringUtil

class BodyBuilder(private val factory: PsiElementFactory) {

  private fun expressionOf(expression: String) = factory.createExpressionFromText(expression, null)

  private fun statementOf(statement: String) = factory.createStatementFromText(statement, null)

  private fun findSubstitutionForExitStatement(flowOutput: FlowOutput, dataOutput: DataOutput, statement: PsiStatement): String {
    val returnExpression = getReturnedExpression(statement)?.text ?: "null"
    return when (flowOutput) {
      is ConditionalFlow -> when (dataOutput) {
        is VariableOutput -> "return null;"
        ArtificialBooleanOutput -> "return true;"
        is ExpressionOutput -> "return $returnExpression;"
        is EmptyOutput -> throw IllegalStateException()
      }
      is UnconditionalFlow -> when (dataOutput) {
        is VariableOutput, is EmptyOutput -> "return;"
        is ExpressionOutput -> "return $returnExpression;"
        ArtificialBooleanOutput -> throw IllegalStateException()
      }
      is EmptyFlow -> throw IllegalStateException()
    }
  }

  private fun castNumericReturns(codeFragment: PsiElement, returnType: PsiType) {
    val castType = PsiPrimitiveType.getUnboxedType(returnType) ?: return
    val returnStatements = PsiTreeUtil.findChildrenOfType(codeFragment, PsiReturnStatement::class.java)
    returnStatements.mapNotNull { it.returnValue }
      .filter { expression -> expression.type != PsiType.NULL && TypeConversionUtil.isNumericType(expression.type) }
      .filterNot { expression -> TypeConversionUtil.isAssignable(returnType, expression.type ?: PsiType.NULL) }
      .forEach { expression -> AddTypeCastFix.addTypeCast(expression.project, expression, castType) }
  }

  private fun replaceExitStatements(flowOutput: FlowOutput, dataOutput: DataOutput) {
    flowOutput.statements.forEach { statement ->
      val replacement = findSubstitutionForExitStatement(flowOutput, dataOutput, statement)
      statement.replace(statementOf(replacement))
    }
  }

  private fun replaceParameterExpressions(parameter: InputParameter) {
    parameter.references
      .map { parameterExpression ->
        CommonJavaRefactoringUtil.outermostParenthesizedExpression(parameterExpression)
      }
      .forEach { normalizedExpression -> normalizedExpression.replace(expressionOf(parameter.name)) }
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
    val statement = if (shouldBeReturned) createReturnStatement(expression) else createExpressionStatement(expression)
    val block = factory.createCodeBlockFromText("{\n}", expression.context)
    val addedStatement = block.add(statement) as PsiStatement
    val inCopyExpression = when (addedStatement) {
      is PsiReturnStatement -> addedStatement.returnValue!!
      is PsiExpressionStatement -> addedStatement.expression
      else -> throw IllegalStateException()
    }
    return Pair(addedStatement, inCopyExpression)
  }

  private fun createReturnStatement(returnExpression: PsiExpression): PsiReturnStatement {
    val statement = factory.createStatementFromText("return dummy;", returnExpression.context) as PsiReturnStatement
    statement.returnValue?.replace(returnExpression)
    return statement
  }

  private fun createExpressionStatement(callExpression: PsiExpression): PsiExpressionStatement {
    val expressionStatement = factory.createStatementFromText("dummy();", callExpression.context) as PsiExpressionStatement
    expressionStatement.expression.replace(callExpression)
    return expressionStatement
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
      val parameterMarkers = inputParameters.associateWith { parameter -> createMarkers(parameter.references) }
      val needsReturnStatement = dataOutput.type != PsiType.VOID
      val (wrappedStatement, wrappedExpression) = wrapExpression(normalizedExpression, needsReturnStatement)
      val wrappedParameters = parameterMarkers.entries.map { (parameter, markers) ->
        parameter.copy(references = releaseMarkers(wrappedExpression, markers))
      }
      val wrappedFlowOutput = if (needsReturnStatement) UnconditionalFlow(listOf(wrappedStatement), true) else EmptyFlow
      val wrappedDataOutput = if (needsReturnStatement) dataOutput.copy(returnExpressions = listOf(wrappedExpression)) else EmptyOutput()
      return build(listOf(wrappedStatement), wrappedFlowOutput, wrappedDataOutput, wrappedParameters, disabledParameters, missedDeclarations)
    }

    val blockStatement = elements.singleOrNull() as? PsiBlockStatement
    val firstElement = blockStatement?.codeBlock?.firstBodyElement ?: elements.first()
    val lastElement = blockStatement?.codeBlock?.lastBodyElement ?: elements.last()
    val normalizedElements = PsiTreeUtil.getElementsOfRange(firstElement, lastElement)
      .dropWhile { it is PsiWhiteSpace }
      .dropLastWhile { it is PsiWhiteSpace }

    val exitStatementsMarkers = createMarkers(flowOutput.statements)
    val parameterMarkers = inputParameters.associateWith { parameter -> createMarkers(parameter.references) }

    val copy = copyOf(normalizedElements)
    val block = copy.first().parent as PsiCodeBlock

    val exitStatementsInCopy: List<PsiStatement> = releaseMarkers(block, exitStatementsMarkers)
    val inCopyFlowOutput = when (flowOutput) {
      is ConditionalFlow -> ConditionalFlow(exitStatementsInCopy)
      is UnconditionalFlow -> UnconditionalFlow(exitStatementsInCopy, flowOutput.isDefaultExit)
      EmptyFlow -> EmptyFlow
    }

    val inCopyInputGroups = parameterMarkers.entries.map { (parameter, marks) ->
      parameter.copy(references = releaseMarkers(block, marks))
    }
    val requiredDeclarations = missedDeclarations.map { createDeclaration(it) }
    inCopyInputGroups.forEach { replaceParameterExpressions(it) }
    replaceExitStatements(inCopyFlowOutput, dataOutput)

    castNumericReturns(block, dataOutput.type)

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

  private fun createMarkers(elements: List<PsiElement>): List<Any> {
    return elements.map(PsiTreeUtil::mark)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T: PsiElement> releaseMarkers(root: PsiElement, marks: List<Any>): List<T> {
    return marks.map { mark -> PsiTreeUtil.releaseMark(root, mark) as? T ?: throw IllegalStateException("Copied element not found.") }
  }
}