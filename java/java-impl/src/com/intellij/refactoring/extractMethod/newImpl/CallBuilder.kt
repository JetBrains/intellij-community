// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.psi.*
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

class CallFactory(private val factory: PsiElementFactory) {

  private fun expressionOf(expression: String) = factory.createExpressionFromText(expression, null)

  private fun statementsOf(vararg statements: String) = statements.map { statement -> factory.createStatementFromText(statement, null) }

  private fun createDeclaration(type: PsiType?, name: String, initializer: String): PsiStatement {
    return when {
      type != null -> factory.createVariableDeclarationStatement(name, type, expressionOf(initializer))
      else -> factory.createStatementFromText("$name = $initializer;", null)
    }
  }

  private fun variableDeclaration(methodCall: String, dataOutput: DataOutput): List<PsiStatement> {
    val declaration = when (dataOutput) {
      is VariableOutput -> createDeclaration(dataOutput.type.takeIf { dataOutput.declareType }, dataOutput.name, methodCall)
      is ExpressionOutput -> createDeclaration(dataOutput.type, dataOutput.name, methodCall)
      ArtificialBooleanOutput, EmptyOutput -> null
    }
    return listOfNotNull(declaration)
  }

  private fun conditionalExit(methodCall: String, flow: ConditionalFlow, dataOutput: DataOutput): List<PsiStatement> {
    val exit = when (dataOutput) {
      is VariableOutput -> "if (${dataOutput.name} == null) ${flow.statements.first().text}"
      is ExpressionOutput -> "if (${dataOutput.name} != null) return ${dataOutput.name};"
      ArtificialBooleanOutput -> "if ($methodCall) ${flow.statements.first().text}"
      EmptyOutput -> throw IllegalArgumentException()
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
      EmptyOutput -> when {
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

  private fun declarationsOf(variables: List<PsiVariable>): List<PsiDeclarationStatement> {
    return variables.map { variable -> factory.createVariableDeclarationStatement(requireNotNull(variable.name), variable.type, null) }
  }

  fun buildCall(methodCall: String, flowOutput: FlowOutput, dataOutput: DataOutput, exposedDeclarations: List<PsiVariable>): List<PsiStatement> {
    val variableDeclaration = if (flowOutput !is ConditionalFlow && dataOutput is ExpressionOutput) emptyList() else variableDeclaration(methodCall, dataOutput)
    return variableDeclaration + createFlowStatements(methodCall, flowOutput, dataOutput) + declarationsOf(exposedDeclarations)
  }
}