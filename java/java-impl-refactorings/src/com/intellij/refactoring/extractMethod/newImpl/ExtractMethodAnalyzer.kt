// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInsight.Nullability
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.findUsedTypeParameters
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.getExpressionType
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.getReturnedExpression
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.guessName
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

  val flowOutput = findFlowOutput(analyzer)
                   ?: throw ExtractException(JavaRefactoringBundle.message("extract.method.error.many.exits"), elements.first())

  val variableData = findVariableData(analyzer, analyzer.findOutputVariables())

  val expression = elements.singleOrNull() as? PsiExpression


  fun canExtractStatementsFromScope(statements: List<PsiStatement>, scope: List<PsiElement>): Boolean {
    return ExtractMethodHelper.areSemanticallySame(statements) && !haveReferenceToScope(statements, scope)
  }

  val dataOutput = when {
    expression != null  -> ExpressionOutput(getExpressionType(expression), null, listOf(expression), CodeFragmentAnalyzer.inferNullability(listOf(expression)))
    variableData is VariableOutput -> when {
      variableData.nullability != Nullability.NOT_NULL && flowOutput is ConditionalFlow -> null
      flowOutput is ConditionalFlow && ! canExtractStatementsFromScope(flowOutput.statements, elements) -> null
      flowOutput is ConditionalFlow -> variableData.copy(nullability = Nullability.NULLABLE)
      else -> variableData
    }
    else -> findFlowData(analyzer, flowOutput)
  }
  if (dataOutput == null) {
    val outputVariable = (variableData as? VariableOutput)?.variable
    throw ExtractException(JavaRefactoringBundle.message("extract.method.error.many.exits"), flowOutput.statements + listOfNotNull(outputVariable))
  }

  val anchor = findClassMember(elements.first())
                        ?: throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.not.found"), elements.first().containingFile)

  var extractOptions = ExtractOptions(anchor, elements, flowOutput, dataOutput)

  val inputParameters = analyzer.findExternalReferences()
    .map { externalReference -> inputParameterOf(externalReference) }
    .map { it.copy(type = normalizeType(it.type)) }
  val parameterNames = inputParameters.map { it.name }.toSet()

  val outputVariable = (dataOutput as? VariableOutput)?.variable
  val exposedVariables = analyzer.findExposedLocalDeclarations().filter { exposedVariable -> exposedVariable != outputVariable }

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

  val localWriteViolations = analyzer.findInstanceMemberUsages(targetClass, elements)
    .filter { localUsage -> PsiUtil.isAccessedForWriting(localUsage.reference) && localUsage.member.hasModifierProperty(PsiModifier.FINAL) }

  val fieldViolations = localWriteViolations.mapNotNull { it.member as? PsiField }.distinct()
  val field = fieldViolations.singleOrNull()
  extractOptions = when {
    fieldViolations.isEmpty() -> extractOptions
    field != null && extractOptions.dataOutput is EmptyOutput ->
      extractOptions.copy(dataOutput = VariableOutput(field.type, field, false), requiredVariablesInside = listOf(field))
    else -> throw ExtractException(JavaRefactoringBundle.message("extract.method.error.many.finals"), localWriteViolations.map { it.reference })
  }

  checkLocalClass(extractOptions)

  val foldedParameters = ExtractMethodPipeline.foldParameters(extractOptions.inputParameters, LocalSearchScope(extractOptions.elements.toTypedArray()))
  extractOptions = extractOptions.copy(inputParameters = foldedParameters)

  return ExtractMethodPipeline.withDefaultStatic(extractOptions)
}

private fun normalizeDataOutput(dataOutput: DataOutput, flowOutput: FlowOutput, elements: List<PsiElement>, reservedNames: List<String>): DataOutput {
  var normalizedDataOutput = dataOutput
  if (flowOutput is ConditionalFlow && dataOutput.type is PsiPrimitiveType) {
    val variableOutput = dataOutput as? VariableOutput
    if (variableOutput?.declareType == false) {
      throw ExtractException(JavaRefactoringBundle.message("extract.method.error.many.exits"), flowOutput.statements)
    }
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

private fun findCommonType(first: PsiType, second: PsiType, nullability: Nullability, manager: PsiManager): PsiType? {
  return if (TypeConversionUtil.isNumericType(first) && TypeConversionUtil.isNumericType(second) && nullability == Nullability.NOT_NULL) {
    TypeConversionUtil.binaryNumericPromotion(first, second)
  } else {
    GenericsUtil.getLeastUpperBound(first, second, manager)
  }
}

private fun findOutputFromReturn(returnStatements: List<PsiStatement>): ExpressionOutput? {
  val returnExpressions = returnStatements.mapNotNull(::getReturnedExpression).sortedBy { returnExpression -> returnExpression.startOffset }

  val context = returnExpressions.firstOrNull() ?: return null
  val manager = context.manager

  val variableName = returnExpressions.asSequence().map { expression -> guessName(expression) }.firstOrNull() ?: "x"

  val codeReturnType = PsiTypesUtil.getMethodReturnType(context) ?: PsiType.getJavaLangObject(manager, GlobalSearchScope.allScope(manager.project))
  val nullability = if (codeReturnType is PsiPrimitiveType) {
    Nullability.NOT_NULL
  } else {
    CodeFragmentAnalyzer.inferNullability(returnExpressions)
  }

  val inferredType = returnExpressions.map { it.type ?: PsiType.NULL }
                    .reduce { commonType, type -> findCommonType(commonType, type, nullability, manager) ?: codeReturnType }

  val returnType = when {
    ! TypeConversionUtil.isAssignable(codeReturnType, inferredType) -> codeReturnType
    inferredType is PsiPrimitiveType && nullability != Nullability.NOT_NULL -> inferredType.getBoxedType(context) ?: codeReturnType
    else -> inferredType
  }

  return ExpressionOutput(returnType, variableName, returnExpressions, nullability)
}

private fun findFlowData(analyzer: CodeFragmentAnalyzer, flowOutput: FlowOutput): DataOutput {
  val returnOutput = findOutputFromReturn(flowOutput.statements)
  return when (flowOutput) {
    is ConditionalFlow -> when {
      returnOutput?.nullability == Nullability.NOT_NULL && returnOutput.type != PsiType.BOOLEAN -> returnOutput.copy(nullability = Nullability.NULLABLE)
      ExtractMethodHelper.areSame(flowOutput.statements) && !ExtractMethodHelper.hasReferencesToScope(analyzer.elements, returnOutput?.returnExpressions.orEmpty()) ->
        ArtificialBooleanOutput
      else -> throw ExtractException(JavaRefactoringBundle.message("extract.method.error.many.exits"), analyzer.elements.first())
    }
    is UnconditionalFlow -> returnOutput ?: EmptyOutput()
    EmptyFlow -> EmptyOutput()
  }
}

private fun findVariableData(analyzer: CodeFragmentAnalyzer, variables: List<PsiVariable>): DataOutput {
  val variable = when {
    analyzer.elements.singleOrNull() is PsiExpression && variables.isNotEmpty() ->
      throw ExtractException(JavaRefactoringBundle.message("extract.method.error.variable.in.expression"), variables)
    variables.size > 1 -> throw ExtractException(JavaRefactoringBundle.message("extract.method.error.many.outputs"), variables)
    variables.isEmpty() -> return EmptyOutput()
    else -> variables.single()
  }
  val nullability = CodeFragmentAnalyzer.inferNullability(analyzer.elements.last(), variable.name)
  return VariableOutput(variables.single().type, variables.single(), variables.single() in analyzer, nullability)
}

private fun PsiModifierListOwner?.hasNullabilityAnnotation(): Boolean {
  if (this == null) return false
  val nullabilityManager = NullableNotNullManager.getInstance(project)
  val nullabilityAnnotations = nullabilityManager.notNulls + nullabilityManager.nullables
  return AnnotationUtil.isAnnotated(this, nullabilityAnnotations, AnnotationUtil.CHECK_TYPE)
}

internal fun updateMethodAnnotations(method: PsiMethod, inputParameters: List<InputParameter>) {
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

private fun checkLocalClass(options: ExtractOptions) {
  val container: PsiElement? = PsiTreeUtil.getParentOfType(options.elements.first(), PsiMember::class.java)
  val analyzer = CodeFragmentAnalyzer(options.elements)
  val localClasses = findLocalClassesIn(container)
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
      throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.outside.defined"), extractedReferences)
    }
    if (remainingReferences.isNotEmpty()) {
      throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.outside.used"), remainingReferences)
    }
    if (classExtracted) {
      analyzer.findUsedVariablesAfter()
        .filter { isExtracted(it) && PsiUtil.resolveClassInType(it.type) === localClass }
        .forEach { throw ExtractException(JavaRefactoringBundle.message("extract.method.error.class.outside.used"), it) }
    }
  }
}

private fun findLocalClassesIn(container: PsiElement?): List<PsiClass> {
  val localClasses = mutableListOf<PsiClass>()
  container?.accept(object : JavaRecursiveElementWalkingVisitor() {
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
  return localClasses.filter(PsiUtil::isLocalClass)
}