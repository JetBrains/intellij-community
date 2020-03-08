// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.Nullability
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.hasExplicitModifier
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.withBoxedType
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.RefactoringUtil

fun findExtractOptions(elements: List<PsiElement>): ExtractOptions? {
  val analyzer = CodeFragmentAnalyzer(elements)

  val flowOutput = findFlowOutput(analyzer) ?: return null

  val variableData = findVariableData(analyzer, analyzer.findOutputVariables()) ?: return null

  val expression = elements.singleOrNull() as? PsiExpression
  val dataOutput = when {
    expression != null -> ExpressionOutput(ExtractMethodHelper.getExpressionType(expression), "x", listOf(expression))
    variableData != EmptyOutput -> when {
      ExtractMethodHelper.areSame(flowOutput.statements) -> variableData
      else -> return null
    }
    else -> findFlowData(analyzer, flowOutput) ?: return null
  }

  val boxedDataOutput = when (flowOutput) {
    is ConditionalFlow -> dataOutput.withBoxedType()
    else -> dataOutput
  }

  if (flowOutput is ConditionalFlow && !isNotNullData(analyzer, dataOutput)) return null

  val anchor = findDefaultAnchor(elements.first()) ?: return null

  fun findUsedTypeParameters(source: PsiTypeParameterList?, searchScope: List<PsiElement>): List<PsiTypeParameter> {
    return RefactoringUtil
      .createTypeParameterListWithUsedTypeParameters(source, *searchScope.toTypedArray())?.typeParameters.orEmpty().toList()
  }

  val typeParameters = PsiElementFactory.getInstance(elements.first().project).createTypeParameterList()
  if (anchor is PsiMethod){
    val classTypeParameters = findUsedTypeParameters(anchor.containingClass?.typeParameterList, elements)
    val methodTypeParameters = findUsedTypeParameters(anchor.typeParameterList, elements)
    (classTypeParameters + methodTypeParameters).forEach { typeParameters.add(it) }
  }

  val inputParameters = analyzer.findExternalReferences().map { externalReference -> inputParameterOf(externalReference) }
  val parameterNames = inputParameters.map { it.name }.toSet()

  val extractOptions = ExtractOptions(
    anchor = anchor,
    elements = elements,
    flowOutput = flowOutput,
    dataOutput = boxedDataOutput,
    thrownExceptions = analyzer.findThrownExceptions(),
    requiredVariablesInside = analyzer.findUndeclaredVariables().filterNot { it.name in parameterNames },
    typeParameters = typeParameters,
    methodName = "extracted",
    isConstructor = false,
    isStatic = false,
    visibility = "private",
    inputParameters = analyzer.findExternalReferences().map { externalReference -> inputParameterOf(externalReference) },
    exposedLocalVariables = analyzer.findExposedLocalDeclarations()
  )

  val targetClass = PsiTreeUtil.getParentOfType(ExtractMethodHelper.getValidParentOf(elements.first()), PsiClass::class.java)!!

  val fieldUsages = analyzer.findFieldUsages(targetClass, elements)
  if (!extractOptions.isConstructor && fieldUsages.any { it.isWrite && it.field.hasExplicitModifier("final") }) {
    return null
  }

  return extractOptions.copy(inputParameters = extractOptions.inputParameters).withDefaultStatic()
}

private fun findDefaultAnchor(element: PsiElement): PsiMember? {
  val holderTypes = arrayOf(PsiMethod::class.java, PsiField::class.java, PsiClassInitializer::class.java)
  return PsiTreeUtil.getNonStrictParentOfType(ExtractMethodHelper.getValidParentOf(element), *holderTypes)
}

private fun findFlowOutput(analyzer: CodeFragmentAnalyzer): FlowOutput? {
  val (exitStatements, numberOfExits, hasSpecialExits) = analyzer.findExitDescription()
  return when (numberOfExits) {
    1 -> if (exitStatements.isNotEmpty()) UnconditionalFlow(exitStatements, !hasSpecialExits) else EmptyFlow
    2 -> ConditionalFlow(exitStatements)
    else -> return null
  }
}

private fun inputParameterOf(externalReference: ExternalReference) = with(externalReference) {
  InputParameter(references, requireNotNull(variable.name), variable.type)
}

private fun findFlowData(analyzer: CodeFragmentAnalyzer, flowOutput: FlowOutput): DataOutput? {
  val returnExpressions = flowOutput.statements.mapNotNull { statement -> (statement as? PsiReturnStatement)?.returnValue }
  val returnType = returnExpressions.asSequence().mapNotNull { expression -> expression.type }.firstOrNull()
  val variableName = returnExpressions.asSequence().map { expression -> ExtractMethodHelper.guessName(expression) }.firstOrNull() ?: "out"
  val returnOutput = if (returnType != null) ExpressionOutput(returnType, variableName, returnExpressions) else null
  return when (flowOutput) {
    is ConditionalFlow -> when {
      ExtractMethodHelper.areSame(
        flowOutput.statements) && analyzer.findExposedLocalVariables(returnExpressions).isEmpty() -> ArtificialBooleanOutput
      CodeFragmentAnalyzer.inferNullability(returnExpressions) == Nullability.NOT_NULL -> returnOutput ?: return null
      else -> null
    }
    is UnconditionalFlow -> returnOutput ?: EmptyOutput
    EmptyFlow -> EmptyOutput
  }
}

private fun findVariableData(analyzer: CodeFragmentAnalyzer, variables: List<PsiVariable>): DataOutput? {
  return when {
    variables.isEmpty() -> EmptyOutput
    variables.size == 1 -> VariableOutput(variables.single().type, variables.single(), variables.single() in analyzer)
    else -> null
  }
}

private fun isNotNullData(analyzer: CodeFragmentAnalyzer, dataOutput: DataOutput): Boolean {
  return when (dataOutput) {
    is VariableOutput -> CodeFragmentAnalyzer.inferNullability(analyzer.elements, dataOutput.variable.name) == Nullability.NOT_NULL
    is ExpressionOutput -> CodeFragmentAnalyzer.inferNullability(dataOutput.returnExpressions) == Nullability.NOT_NULL
    is ArtificialBooleanOutput, is EmptyOutput -> true
  }
}

internal fun updateMethodAnnotations(method: PsiMethod) {
  if (method.returnType !is PsiPrimitiveType) {
    val resultNullability = CodeFragmentAnalyzer.inferNullability(CodeFragmentAnalyzer.findReturnExpressionsIn(method))
    ExtractMethodHelper.addNullabilityAnnotation(method, resultNullability)
  }
}