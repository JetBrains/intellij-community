// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.createDeclaration
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*

class CallBuilder(project: Project, private val context: PsiElement?) {

  private val factory: PsiElementFactory = PsiElementFactory.getInstance(project)

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
    if (dataOutput is VariableOutput && declaredVariable != null) {
      val needsFinal = dataOutput.variable.hasModifierProperty(PsiModifier.FINAL)
      PsiUtil.setModifierProperty(declaredVariable, PsiModifier.FINAL, needsFinal)
    }
    return listOfNotNull(declaration)
  }

  private fun conditionalExit(methodCall: String, flow: ConditionalFlow, dataOutput: DataOutput): List<PsiStatement> {
    val exit = when (dataOutput) {
      is VariableOutput -> "if (${dataOutput.name} == null) ${flow.statements.first().text}"
      is ExpressionOutput -> "if (${dataOutput.name} != null) return ${dataOutput.name};"
      ArtificialBooleanOutput -> "if ($methodCall) ${flow.statements.first().text}"
      is EmptyOutput -> throw IllegalArgumentException()
    }
    return statementsOf(exit)
  }

  private fun unconditionalExit(methodCall: String, flow: UnconditionalFlow, dataOutput: DataOutput): List<PsiStatement> {
    return when (dataOutput) {
      is VariableOutput -> statementsOf(
        flow.statements.first().text
      )
      is ExpressionOutput -> statementsOf(
        "return $methodCall;"
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

  fun buildCall(methodCall: String, flowOutput: FlowOutput, dataOutput: DataOutput, exposedDeclarations: List<PsiVariable>): List<PsiStatement> {
    val variableDeclaration = if (flowOutput !is ConditionalFlow && dataOutput is ExpressionOutput) emptyList() else variableDeclaration(methodCall, dataOutput)
    return variableDeclaration + createFlowStatements(methodCall, flowOutput, dataOutput) + exposedDeclarations.map { createDeclaration(it) }
  }

  fun buildExpressionCall(methodCall: String, dataOutput: DataOutput): List<PsiElement> {
    require(dataOutput is ExpressionOutput)
    val expression = if (dataOutput.name != null) "${dataOutput.name} = $methodCall" else methodCall
    return listOf(factory.createExpressionFromText(expression, context))
  }
}