// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.PrepareFailedException
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.findUsedTypeParameters
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.getExpressionType
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.guessName
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.hasExplicitModifier
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.haveReferenceToScope
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.inputParameterOf
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.normalizedAnchor
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.uniqueNameOf
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.withBoxedType
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.FlowOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Processor
import java.util.*

fun findExtractOptions(elements: List<PsiElement>): ExtractOptions {
  require(elements.isNotEmpty())
  val analyzer = CodeFragmentAnalyzer(elements)

  val flowOutput = findFlowOutput(analyzer) ?: throw PrepareFailedException("too many exits", elements.first())

  val variableData = findVariableData(analyzer, analyzer.findOutputVariables())

  val expression = elements.singleOrNull() as? PsiExpression


  fun canExtractStatementsFromScope(statements: List<PsiStatement>, scope: List<PsiElement>): Boolean {
    return ExtractMethodHelper.areSemanticallySame(statements) && !haveReferenceToScope(statements, scope)
  }

  //TODO use correct error messages
  val dataOutput = when {
    expression != null  -> ExpressionOutput(getExpressionType(expression), null, listOf(expression), CodeFragmentAnalyzer.inferNullability(listOf(expression)))
    variableData is VariableOutput -> when {
      flowOutput is ConditionalFlow && ! canExtractStatementsFromScope(flowOutput.statements, elements)
        -> throw PrepareFailedException("Out var and different flow statements", flowOutput.statements.first())
      variableData.nullability != Nullability.NOT_NULL && flowOutput is ConditionalFlow -> throw PrepareFailedException("Nullable out var and branching", variableData.variable)
      flowOutput is ConditionalFlow -> variableData.copy(nullability = Nullability.NULLABLE)
      else -> variableData
    }
    else -> findFlowData(analyzer, flowOutput)
  }

  val anchor = findClassMember(elements.first()) ?: throw PrepareFailedException("No upper class", elements.first())

  var extractOptions = ExtractOptions(anchor, elements, flowOutput, dataOutput)

  val inputParameters = analyzer.findExternalReferences()
    .map { externalReference -> inputParameterOf(externalReference) }
    .map { it.copy(type = normalizeType(it.type)) }
  val parameterNames = inputParameters.map { it.name }.toSet()

  val exposedVariables = analyzer.findExposedLocalDeclarations()

  extractOptions = extractOptions.copy(
    dataOutput = normalizeDataOutput(dataOutput, flowOutput, elements, exposedVariables.mapNotNull { it.name }),
    thrownExceptions = analyzer.findThrownExceptions(),
    requiredVariablesInside = analyzer.findUndeclaredVariables().filterNot { it.name in parameterNames },
    typeParameters = findUsedTypeParameters((anchor as? PsiTypeParameterListOwner)?.typeParameterList, elements),
    inputParameters = inputParameters,
    exposedLocalVariables = exposedVariables
  )

  extractOptions = ExtractMethodPipeline.withCastedParameters(extractOptions)

  val targetClass = PsiTreeUtil.getParentOfType(ExtractMethodHelper.getValidParentOf(elements.first()), PsiClass::class.java)!!

  val fieldUsages = analyzer.findFieldUsages(targetClass, elements)
  val finalFields = fieldUsages
    .filter { it.isWrite && it.field.hasExplicitModifier(PsiModifier.FINAL) }
    .map { it.field }
    .distinct()
  val field = finalFields.singleOrNull()
  extractOptions = when {
    finalFields.isEmpty() -> extractOptions
    field != null && extractOptions.dataOutput is EmptyOutput ->
      extractOptions.copy(dataOutput = VariableOutput(field.type, field, false), requiredVariablesInside = listOf(field))
    else -> throw PrepareFailedException("Too many final fields", finalFields.first())
  }

  checkLocalClass(extractOptions)

  return ExtractMethodPipeline.withDefaultStatic(extractOptions)
}

private fun normalizeDataOutput(dataOutput: DataOutput, flowOutput: FlowOutput, elements: List<PsiElement>, reservedNames: List<String>): DataOutput {
  var normalizedDataOutput = dataOutput
  if (flowOutput is ConditionalFlow && dataOutput.type is PsiPrimitiveType) {
    val variableOutput = dataOutput as? VariableOutput
    if (variableOutput?.declareType == false) throw PrepareFailedException("Too many outputs (TODO)", variableOutput.variable)
    normalizedDataOutput = dataOutput.withBoxedType()
  }
  if (normalizedDataOutput is ExpressionOutput) {
    normalizedDataOutput = normalizedDataOutput.copy(name = uniqueNameOf(normalizedDataOutput.name, elements, reservedNames))
  }
  return normalizedDataOutput
}

private fun normalizeType(type: PsiType): PsiType {
  return if (type is PsiDisjunctionType) {
    PsiTypesUtil.getLowestUpperBoundClassType(type)!!
  } else {
    GenericsUtil.getVariableTypeByExpressionType(type)
  }
}

fun findClassMember(element: PsiElement): PsiMember? {
  val holderTypes = arrayOf(PsiMethod::class.java, PsiField::class.java, PsiClassInitializer::class.java)
  val anchor = PsiTreeUtil.getNonStrictParentOfType(ExtractMethodHelper.getValidParentOf(element), *holderTypes) ?: return null
  return normalizedAnchor(anchor)
}

private fun findFlowOutput(analyzer: CodeFragmentAnalyzer): FlowOutput? {
  if (analyzer.hasObservableThrowExit()) return null
  val (exitStatements, numberOfExits, hasSpecialExits) = analyzer.findExitDescription()
  return when (numberOfExits) {
    1 -> if (exitStatements.isNotEmpty()) UnconditionalFlow(exitStatements, !hasSpecialExits) else EmptyFlow
    2 -> if (exitStatements.isNotEmpty()) ConditionalFlow(exitStatements) else null
    else -> return null
  }
}

private fun findOutputFromReturn(flowOutput: FlowOutput): ExpressionOutput? {
  val returnExpressions = flowOutput.statements
    .mapNotNull { statement -> (statement as? PsiReturnStatement)?.returnValue }
    .sortedBy { returnStatement -> returnStatement.startOffset }
  val returnType = returnExpressions.asSequence().mapNotNull { expression -> expression.type }.firstOrNull()
  val variableName = returnExpressions.asSequence().map { expression -> guessName(expression) }.firstOrNull() ?: "x"
  val nullability = CodeFragmentAnalyzer.inferNullability(returnExpressions)
  return if (returnType != null) ExpressionOutput(returnType, variableName, returnExpressions, nullability) else null
}

private fun findFlowData(analyzer: CodeFragmentAnalyzer, flowOutput: FlowOutput): DataOutput {
  val returnOutput = findOutputFromReturn(flowOutput)
  return when (flowOutput) {
    is ConditionalFlow -> when {
      returnOutput?.nullability == Nullability.NOT_NULL && returnOutput.type != PsiType.BOOLEAN -> returnOutput.copy(nullability = Nullability.NULLABLE)
      ExtractMethodHelper.areSame(flowOutput.statements) && analyzer.findExposedLocalVariables(returnOutput?.returnExpressions.orEmpty()).isEmpty() ->
        ArtificialBooleanOutput
      else -> throw PrepareFailedException("Nullable output and branching", analyzer.elements.first())
    }
    is UnconditionalFlow -> returnOutput ?: EmptyOutput()
    EmptyFlow -> EmptyOutput()
  }
}

//TODO correct messages in PrepareFailedException
private fun findVariableData(analyzer: CodeFragmentAnalyzer, variables: List<PsiVariable>): DataOutput {
  val variable = when {
    analyzer.elements.singleOrNull() is PsiExpression && variables.isNotEmpty() -> throw PrepareFailedException("Var in expression", variables.first())
    variables.isEmpty() -> return EmptyOutput()
    variables.size > 1 -> throw PrepareFailedException("Many vars", variables[1])
    else -> variables.single()
  }
  val nullability = CodeFragmentAnalyzer.inferNullability(analyzer.elements.last() as PsiStatement, variable.name)
  return VariableOutput(variables.single().type, variables.single(), variables.single() in analyzer, nullability)
}

private fun PsiModifierListOwner?.hasNullabilityAnnotation(): Boolean {
  if (this == null) return false
  val nullabilityManager = NullableNotNullManager.getInstance(project)
  val nullabilityAnnotations = nullabilityManager.notNulls + nullabilityManager.nullables
  return AnnotationUtil.isAnnotated(this, nullabilityAnnotations, AnnotationUtil.CHECK_TYPE)
}

internal fun updateMethodAnnotations(method: PsiMethod,inputParameters: List<InputParameter>) {
  if (method.returnType !is PsiPrimitiveType) {
    //TODO use dataoutput.nullability instead
    val resultNullability = CodeFragmentAnalyzer.inferNullability(CodeFragmentAnalyzer.findReturnExpressionsIn(method))
    ExtractMethodHelper.addNullabilityAnnotation(method, resultNullability)
  }
  val parameters = method.parameterList.parameters
  inputParameters
    .filter { ((it.references.first() as? PsiReferenceExpression)?.resolve() as? PsiModifierListOwner).hasNullabilityAnnotation() }
    .forEach { inputParameter ->
      val parameterNullability = CodeFragmentAnalyzer.inferNullability(inputParameter.references)
      val parameter = parameters.find { it.name == inputParameter.name }
      if (parameter != null) ExtractMethodHelper.addNullabilityAnnotation(parameter, parameterNullability)
    }
}

private fun checkLocalClass(options: ExtractOptions): Boolean {
  var container: PsiElement? = PsiTreeUtil.getParentOfType(options.elements.first(), PsiClass::class.java, PsiMethod::class.java)
  while (container is PsiMethod && container.containingClass !== options.anchor.parent) {
    container = PsiTreeUtil.getParentOfType(container, PsiMethod::class.java, true)
  }
  container ?: return true
  val analyzer = CodeFragmentAnalyzer(options.elements)
  val localClasses = mutableListOf<PsiClass>()
  container.accept(object : JavaRecursiveElementWalkingVisitor() {
    override fun visitClass(aClass: PsiClass) {
      localClasses.add(aClass)
    }

    override fun visitAnonymousClass(aClass: PsiAnonymousClass) {
      visitElement(aClass)
    }

    override fun visitTypeParameter(classParameter: PsiTypeParameter) {
      visitElement(classParameter)
    }
  })
  fun isExtracted(element: PsiElement): Boolean {
    return element.textRange in TextRange(options.elements.first().textRange.startOffset, options.elements.last().textRange.endOffset)
  }
  for (localClass in localClasses) {
    val classExtracted: Boolean = isExtracted(localClass)
    val extractedReferences = Collections.synchronizedList(ArrayList<PsiElement>())
    val remainingReferences = Collections.synchronizedList(ArrayList<PsiElement>())
    ReferencesSearch.search(localClass).forEach(Processor { psiReference: PsiReference ->
      val element = psiReference.element
      val elementExtracted: Boolean = isExtracted(element)
      if (elementExtracted && !classExtracted) {
        extractedReferences.add(element)
        return@Processor false
      }
      if (!elementExtracted && classExtracted) {
        remainingReferences.add(element)
        return@Processor false
      }
      true
    })
    if (extractedReferences.isNotEmpty()) {
      throw PrepareFailedException(
        "Cannot extract method because the selected code fragment uses local classes defined outside of the fragment",
        extractedReferences[0])
    }
    if (remainingReferences.isNotEmpty()) {
      throw PrepareFailedException(
        "Cannot extract method because the selected code fragment defines local classes used outside of the fragment",
        remainingReferences[0])
    }
    if (classExtracted) {
      analyzer.findUsedVariablesAfter()
        .filter { isExtracted(it) && PsiUtil.resolveClassInType(it.type) === localClass }
        .forEach {
          throw PrepareFailedException(
            "Cannot extract method because the selected code fragment defines variable of local class type used outside of the fragment", it
          )
        }
    }
  }
  return true
}