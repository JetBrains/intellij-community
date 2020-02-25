// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.hasExplicitModifier
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.VariableData

private fun findFoldableArrayExpression(reference: PsiElement): PsiArrayAccessExpression? {
  val arrayAccess = reference.parent as? PsiArrayAccessExpression
  return when (arrayAccess?.arrayExpression) {
    reference -> arrayAccess
    else -> null
  }
}

fun remap(extractOptions: ExtractOptions,
          variableData: Array<VariableData>,
          methodName: String,
          isStatic: Boolean,
          visibility: String,
          isConstructor: Boolean): ExtractOptions {
  val analyzer = CodeFragmentAnalyzer(extractOptions.elements)
  val remappedName = extractOptions.withMappedName(methodName)
  val remappedInput = remappedName.withMappedParametersInput(variableData.toList())
  val remappedStatic = remappedInput.takeIf { isStatic }
                          ?.withForcedStatic(analyzer = analyzer)
                          ?: remappedInput
  val mappedToConstructor = remappedStatic.takeIf { isConstructor }?.asConstructor(analyzer) ?: remappedStatic

  return mappedToConstructor.copy(visibility = visibility)
}

fun ExtractOptions.withMappedParametersInput(variablesData: List<VariableData>): ExtractOptions {
  fun findMappedParameter(variableData: VariableData): InputParameter? {
    return inputParameters
      .find { (it.references.first() as? PsiReferenceExpression)?.text == variableData.variable.name }
      ?.copy(name = variableData.name ?: "x", type = variableData.type)
  }

  val mappedParameters = variablesData.mapNotNull(::findMappedParameter)

  if (mappedParameters.size != inputParameters.size) return this

  return copy(
    inputParameters = mappedParameters
  )
}

fun ExtractOptions.withMappedName(methodName: String) = if (this.isConstructor) this else this.copy(methodName = methodName)

fun ExtractOptions.withDefaultStatic(): ExtractOptions {
  val parent = ExtractMethodHelper.getValidParentOf(elements.first())
  val parentHolder = PsiTreeUtil.getNonStrictParentOfType(parent, PsiMethod::class.java, PsiClassInitializer::class.java,
                                                          PsiField::class.java)
  val shouldBeStatic = when {
    parent is PsiField -> true
    parentHolder.hasExplicitModifier(PsiModifier.STATIC) -> true
    else -> false
  }
  return copy(isStatic = shouldBeStatic)
}

fun ExtractOptions.withFoldedArrayParameters(analyzer: CodeFragmentAnalyzer): ExtractOptions {
  val writtenVariables = analyzer.findWrittenVariables().mapNotNull { it.name }

  fun findFoldedCandidate(inputParameter: InputParameter): InputParameter? {
    val arrayAccesses = inputParameter.references.map { findFoldableArrayExpression(it) ?: return null }
    if (arrayAccesses.any { (it.parent as? PsiAssignmentExpression)?.lExpression == it }) return null
    if (!ExtractMethodHelper.areSame(arrayAccesses.map { it.indexExpression })) return null
    if (arrayAccesses.any { it.indexExpression?.text in writtenVariables }) return null
    val parameterName = arrayAccesses.first().arrayExpression.text + "Element"
    return InputParameter(arrayAccesses, parameterName, arrayAccesses.first().type ?: return null)
  }

  fun findHiddenExpression(arrayAccess: PsiArrayAccessExpression?): List<InputParameter> {
    return inputParameters.filter {
      ExtractMethodHelper.areSame(it.references.first(), arrayAccess?.arrayExpression)
      || ExtractMethodHelper.areSame(it.references.first(), arrayAccess?.indexExpression)
    }
  }

  val foldedCandidates = inputParameters.mapNotNull { findFoldedCandidate(it) }

  val (folded, hidden) = foldedCandidates
    .map { it to findHiddenExpression(it.references.first() as? PsiArrayAccessExpression) }
    .filter { it.second.size > 1 }.unzip()

  return this.copy(inputParameters = this.inputParameters - hidden.flatten() + folded)
}

fun ExtractOptions.asConstructor(analyzer: CodeFragmentAnalyzer): ExtractOptions {
  return if (canBeConstructor(analyzer)) copy(isConstructor = true, methodName = "this") else this
}

fun ExtractOptions.withForcedStatic(analyzer: CodeFragmentAnalyzer): ExtractOptions? {
  val targetClass = PsiTreeUtil.getParentOfType(ExtractMethodHelper.getValidParentOf(elements.first()), PsiClass::class.java)!!
  val fieldUsages = analyzer.findFieldUsages(targetClass, elements)
  if (fieldUsages.any { it.isWrite }) return null
  val fieldInputParameters =
    fieldUsages.groupBy { it.field }.entries.map { (field, fieldUsages) ->
      InputParameter(
        references = fieldUsages.map { it.classMemberReference },
        name = field.name,
        type = field.type
      )
    }
  return copy(inputParameters = inputParameters + fieldInputParameters, isStatic = true)
}

private fun canBeConstructor(analyzer: CodeFragmentAnalyzer): Boolean {
  val elements = analyzer.elements
  val parent = ExtractMethodHelper.getValidParentOf(elements.first())
  val holderClass = PsiTreeUtil.getNonStrictParentOfType(parent, PsiClass::class.java) ?: return false
  val method = PsiTreeUtil.getNonStrictParentOfType(parent, PsiMethod::class.java) ?: return false
  val firstStatement = method.body?.statements?.firstOrNull() ?: return false
  val startsOnBegin = firstStatement.textRange in TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
  val outStatements = method.body?.statements.orEmpty().dropWhile { it.textRange.endOffset <= elements.last().textRange.endOffset }
  val hasOuterFinalFieldAssignments = analyzer
    .findFieldUsages(holderClass, outStatements)
    .any { it.isWrite && it.field.hasExplicitModifier("final") }
  return method.isConstructor && startsOnBegin && !hasOuterFinalFieldAssignments && analyzer.findOutputVariables().isEmpty()
}