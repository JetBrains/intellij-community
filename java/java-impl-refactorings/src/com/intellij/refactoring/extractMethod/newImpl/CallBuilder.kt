// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.resolve.JavaResolveUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.IntroduceVariableUtil
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.createDeclaration
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.util.RefactoringChangeUtil
class CallBuilder(private val context: PsiElement) {

  private val factory: PsiElementFactory = PsiElementFactory.getInstance(context.project)

  private fun expressionOf(expression: String) = factory.createExpressionFromText(expression, context)

  private fun statementsOf(vararg statements: String) = statements.map { statement -> factory.createStatementFromText(statement, context) }

  private fun createDeclaration(type: PsiType?, name: String, initializer: String): PsiStatement {
    return if (type != null) {
      factory.createVariableDeclarationStatement(name, type, expressionOf(initializer))
    } else {
      factory.createStatementFromText("$name = $initializer;", context)
    }
  }

  private fun variableDeclaration(methodCall: String, dataOutput: DataOutput): List<PsiStatement> {
    val declaration = when (dataOutput) {
      is VariableOutput -> createDeclaration(dataOutput.type.takeIf { dataOutput.declareType }, dataOutput.name, methodCall)
      is ExpressionOutput -> createDeclaration(dataOutput.type, dataOutput.name!!, methodCall)
      ArtificialBooleanOutput, is EmptyOutput -> null
    }
    val declarationStatement = declaration as? PsiDeclarationStatement
    val declaredVariable = declarationStatement?.declaredElements?.firstOrNull() as? PsiVariable
    if (declaredVariable != null) {
      val settings = JavaRefactoringSettings.getInstance()
      val outputVariable = (dataOutput as? VariableOutput)?.variable
      val declareFinal = outputVariable?.hasModifierProperty(PsiModifier.FINAL) == true || settings.INTRODUCE_LOCAL_CREATE_FINALS == true
      PsiUtil.setModifierProperty(declaredVariable, PsiModifier.FINAL, declareFinal)

      val isInferredVar = outputVariable?.typeElement?.isInferredType == true
      if (isInferredVar || HighlightingFeature.LVTI.isAvailable(context) && settings.INTRODUCE_LOCAL_CREATE_VAR_TYPE == true) {
        IntroduceVariableUtil.expandDiamondsAndReplaceExplicitTypeWithVar(declaredVariable.typeElement, declaredVariable)
      }
    }
    return listOfNotNull(declaration)
  }

  private fun conditionalExit(methodCall: String, flow: ConditionalFlow, dataOutput: DataOutput): List<PsiStatement> {
    val exit = when (dataOutput) {
      is VariableOutput -> "if (${dataOutput.name} == null) ${flow.statements.first().text}"
      is ExpressionOutput -> "if (${dataOutput.name} != null) ${exitStatementOf(flow)} ${dataOutput.name};"
      ArtificialBooleanOutput -> "if ($methodCall) ${flow.statements.first().text}"
      is EmptyOutput -> throw IllegalArgumentException()
    }
    return statementsOf(exit)
  }

  private fun exitStatementOf(flow: FlowOutput): String {
    return if (flow.statements.firstOrNull() is PsiYieldStatement) "yield" else "return"
  }

  private fun unconditionalExit(methodCall: String, flow: UnconditionalFlow, dataOutput: DataOutput): List<PsiStatement> {
    return when (dataOutput) {
      is VariableOutput -> statementsOf(
        flow.statements.first().text
      )
      is ExpressionOutput -> statementsOf(
        "${exitStatementOf(flow)} $methodCall;"
      )
      ArtificialBooleanOutput -> throw IllegalStateException()
      is EmptyOutput -> when {
        flow.isDefaultExit -> statementsOf("$methodCall;")
        else -> statementsOf(
          "$methodCall;",
          flow.statements.first().text
        )
      }
    }
  }

  private fun createFlowStatements(methodCall: String, flowOutput: FlowOutput, dataOutput: DataOutput): List<PsiStatement> {
    return when (flowOutput) {
      is ConditionalFlow -> conditionalExit(methodCall, flowOutput, dataOutput)
      is UnconditionalFlow -> unconditionalExit(methodCall, flowOutput, dataOutput)
      EmptyFlow -> if (dataOutput !is VariableOutput) statementsOf("$methodCall;") else emptyList()
    }
  }

  private fun buildCall(methodCall: String, flowOutput: FlowOutput, dataOutput: DataOutput, exposedDeclarations: List<PsiVariable>): List<PsiStatement> {
    val variableDeclaration = if (flowOutput !is ConditionalFlow && dataOutput is ExpressionOutput) emptyList() else variableDeclaration(methodCall, dataOutput)
    return variableDeclaration + createFlowStatements(methodCall, flowOutput, dataOutput) + exposedDeclarations.map { createDeclaration(it) }
  }

  private fun buildExpressionCall(methodCall: String, dataOutput: DataOutput): List<PsiElement> {
    require(dataOutput is ExpressionOutput)
    val expression = if (dataOutput.name != null) "${dataOutput.name} = $methodCall" else methodCall
    return listOf(factory.createExpressionFromText(expression, context))
  }

  private fun createMethodCall(method: PsiMethod, parameters: List<PsiExpression>): PsiMethodCallExpression {
    val name = if (method.isConstructor) "this" else method.name
    val callText = name + "(" + parameters.joinToString { it.text } + ")"
    val factory = PsiElementFactory.getInstance(method.project)
    val callElement = factory.createExpressionFromText(callText, context) as PsiMethodCallExpression
    val methodClass = findParentClass(method)!!
    if (methodClass != findParentClass(
        context) && callElement.resolveMethod() != null && callElement.resolveMethod() != method && !method.isConstructor) {
      val ref = if (method.hasModifierProperty(PsiModifier.STATIC)) {
        factory.createReferenceExpression(methodClass)
      }
      else {
        RefactoringChangeUtil.createThisExpression(method.manager, methodClass)
      }
      callElement.methodExpression.qualifierExpression = ref
    }
    return callElement
  }

  private fun findParentClass(context: PsiElement): PsiClass? {
    return JavaResolveUtil.findParentContextOfClass(context, PsiClass::class.java, false) as? PsiClass
  }

  fun createCall(method: PsiMethod, dependencies: ExtractOptions): List<PsiElement> {
    val parameters = dependencies.inputParameters.map { it.references.first() }

    val methodCall = createMethodCall(method, parameters).text
    val expressionElement = (dependencies.elements.singleOrNull() as? PsiExpression)
    val callElements = if (expressionElement != null) {
      buildExpressionCall(methodCall, dependencies.dataOutput)
    }
    else {
      buildCall(methodCall, dependencies.flowOutput, dependencies.dataOutput, dependencies.exposedLocalVariables)
    }

    val styleManager = CodeStyleManager.getInstance(dependencies.project)
    return callElements.map(styleManager::reformat)
  }
}