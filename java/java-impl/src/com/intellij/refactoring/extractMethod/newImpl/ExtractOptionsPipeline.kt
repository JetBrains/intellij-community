// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.AnnotationUtil.*
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.findUsedTypeParameters
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.hasExplicitModifier
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.inputParameterOf
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.normalizedAnchor
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.VariableData

object ExtractMethodPipeline {

  fun remap(extractOptions: ExtractOptions,
            variableData: Array<VariableData>,
            methodName: String,
            isStatic: Boolean,
            visibility: String,
            isConstructor: Boolean,
            returnType: PsiType
  ): ExtractOptions {
    val analyzer = CodeFragmentAnalyzer(extractOptions.elements)
    var options = withMappedName(extractOptions, methodName)
    if (isStatic && ! options.isStatic) {
      options = withForcedStatic(analyzer, options) ?: options
    }
    options = withMappedParametersInput(options, variableData.toList())
    val targetClass = extractOptions.anchor.containingClass!!
    options = if (targetClass.isInterface) {
      adjustModifiersForInterface(options.copy(visibility = PsiModifier.PRIVATE))
    } else {
      options.copy(visibility = visibility)
    }

    if (isConstructor) {
      options = asConstructor(analyzer, options) ?: options
    } else {
      options = options.copy(dataOutput = extractOptions.dataOutput.withType(returnType))
    }
    return options
  }

  fun withTargetClass(analyzer: CodeFragmentAnalyzer, extractOptions: ExtractOptions, targetClass: PsiClass): ExtractOptions? {
    val anchor = extractOptions.anchor
    if (anchor.parent == targetClass) return extractOptions

    val newAnchor = targetClass.children.find { child -> anchor.textRange in child.textRange } as? PsiMember
    if (newAnchor == null) return null

    val typeParameters = findAllTypeLists(anchor, targetClass).flatMap { findUsedTypeParameters(it, extractOptions.elements) }

    val additionalReferences = analyzer.findOuterLocals(anchor, newAnchor) ?: return null
    val additionalParameters = additionalReferences.map { inputParameterOf(it) }
    val options = extractOptions.copy(
      anchor = normalizedAnchor(newAnchor),
      inputParameters = extractOptions.inputParameters + additionalParameters,
      typeParameters = typeParameters
    )
    return withDefaultStatic(options)
  }

  private fun findAllTypeLists(element: PsiElement, stopper: PsiElement): List<PsiTypeParameterList> {
    return generateSequence (element) { it.parent }
      .takeWhile { it != stopper && it !is PsiFile }
      .filterIsInstance<PsiTypeParameterListOwner>()
      .mapNotNull { it.typeParameterList }
      .toList()
  }

  private fun findCommonCastParameter(inputParameter: InputParameter): InputParameter? {
    val castExpressions = inputParameter.references.map { reference -> (reference.parent as? PsiTypeCastExpression) ?: return null }
    val type = castExpressions.first().castType?.type ?: return null
    if ( castExpressions.any { castExpression -> castExpression.castType?.type != type } ) return null
    return InputParameter(name = inputParameter.name, type = type, references = castExpressions)
  }

  fun withCastedParameters(extractOptions: ExtractOptions): ExtractOptions {
    val parameters = extractOptions.inputParameters.map { inputParameter -> findCommonCastParameter(inputParameter) ?: inputParameter }
    return extractOptions.copy(inputParameters = parameters)
  }

  fun withMappedParametersInput(extractOptions: ExtractOptions, variablesData: List<VariableData>): ExtractOptions {
    fun findMappedParameter(variableData: VariableData): InputParameter? {
      return extractOptions.inputParameters
        .find { parameter -> parameter.name == variableData.variable.name }
        ?.copy(name = variableData.name ?: "x", type = variableData.type)
    }

    val mappedParameters = variablesData.filter { it.passAsParameter }.mapNotNull(::findMappedParameter)
    val disabledParameters = variablesData.filterNot { it.passAsParameter }.mapNotNull(::findMappedParameter)

    return extractOptions.copy(
      inputParameters = mappedParameters,
      disabledParameters = disabledParameters
    )
  }

  fun adjustModifiersForInterface(options: ExtractOptions): ExtractOptions {
    val targetClass = options.anchor.containingClass!!
    if (! targetClass.isInterface) return options
    val isJava8 = PsiUtil.getLanguageLevel(targetClass) == LanguageLevel.JDK_1_8
    val visibility = if (options.visibility == PsiModifier.PRIVATE && isJava8) null else options.visibility
    val holder = findClassMember(options.elements.first())
    val isStatic = holder is PsiField || options.isStatic
    return options.copy(visibility = visibility, isStatic = isStatic)
  }

  fun withMappedName(extractOptions: ExtractOptions, methodName: String) = if (extractOptions.isConstructor) extractOptions else extractOptions.copy(methodName = methodName)

  fun withDefaultStatic(extractOptions: ExtractOptions): ExtractOptions {
    val expression = extractOptions.elements.singleOrNull() as? PsiExpression
    val statement = PsiTreeUtil.getParentOfType(expression, PsiExpressionStatement::class.java)
    if (statement != null && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
      return extractOptions.copy(isStatic = true)
    }
    val shouldBeStatic = extractOptions.anchor.hasExplicitModifier(PsiModifier.STATIC)
    return extractOptions.copy(isStatic = shouldBeStatic)
  }

  fun findTargetCandidates(analyzer: CodeFragmentAnalyzer, options: ExtractOptions): List<PsiClass> {
    return generateSequence (options.anchor as PsiElement) { it.parent }
      .takeWhile { it !is PsiFile }
      .filterIsInstance<PsiClass>()
      .filter { targetClass -> withTargetClass(analyzer, options, targetClass) != null }
      .toList()
  }

  fun findDefaultTargetCandidate(candidates: List<PsiClass>): PsiClass {
    return AnonymousTargetClassPreselectionUtil.getPreselection(candidates, candidates.first()) ?: candidates.first()
  }

  fun selectTargetClass(options: ExtractOptions, onSelect: (ExtractOptions) -> Unit): ExtractOptions {
    val analyzer = CodeFragmentAnalyzer(options.elements)
    val targetCandidates = findTargetCandidates(analyzer, options)
    val preselection = findDefaultTargetCandidate(targetCandidates)

    val editor = FileEditorManager.getInstance(options.project).selectedTextEditor ?: return options

    val processor = PsiElementProcessor<PsiClass> { selected ->
      val mappedOptions = withTargetClass(analyzer, options, selected)!!
      onSelect(mappedOptions)
      true
    }

    if (targetCandidates.size > 1) {
      NavigationUtil.getPsiElementPopup(targetCandidates.toTypedArray(), PsiClassListCellRenderer(), "Choose Destination Class", processor, preselection)
        .showInBestPositionFor(editor)
    } else {
      processor.execute(preselection)
    }

    return options
  }

  private fun findFoldableArrayExpression(reference: PsiElement): PsiArrayAccessExpression? {
    val arrayAccess = reference.parent as? PsiArrayAccessExpression
    return if (arrayAccess?.arrayExpression == reference) arrayAccess else null
  }

  fun withFoldedArrayParameters(analyzer: CodeFragmentAnalyzer, extractOptions: ExtractOptions): ExtractOptions {
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
      return extractOptions.inputParameters.filter {
        ExtractMethodHelper.areSame(it.references.first(), arrayAccess?.arrayExpression)
        || ExtractMethodHelper.areSame(it.references.first(), arrayAccess?.indexExpression)
      }
    }

    val foldedCandidates = extractOptions.inputParameters.mapNotNull { findFoldedCandidate(it) }

    val (folded, hidden) = foldedCandidates
      .map { it to findHiddenExpression(it.references.first() as? PsiArrayAccessExpression) }
      .filter { it.second.size > 1 }.unzip()

    return extractOptions.copy(inputParameters = extractOptions.inputParameters - hidden.flatten() + folded)
  }

  fun asConstructor(analyzer: CodeFragmentAnalyzer, extractOptions: ExtractOptions): ExtractOptions? {
    if (! canBeConstructor(analyzer)) return null
    return extractOptions.copy(isConstructor = true,
                               methodName = "this",
                               dataOutput = EmptyOutput(),
                               requiredVariablesInside = emptyList()
    )
  }

  fun withForcedStatic(analyzer: CodeFragmentAnalyzer, extractOptions: ExtractOptions): ExtractOptions? {
    val targetClass = PsiTreeUtil.getParentOfType(ExtractMethodHelper.getValidParentOf(extractOptions.elements.first()), PsiClass::class.java)!!
    val fieldUsages = analyzer.findLocalFieldUsages(targetClass, extractOptions.elements)
    if (fieldUsages.any { it.isWrite }) return null
    val fieldInputParameters =
      fieldUsages.groupBy { it.field }.entries.map { (field, fieldUsages) ->
        InputParameter(
          references = fieldUsages.map { it.classMemberReference },
          name = field.name,
          type = field.type
        )
      }
    return extractOptions.copy(inputParameters = extractOptions.inputParameters + fieldInputParameters, isStatic = true)
  }

  fun canBeConstructor(analyzer: CodeFragmentAnalyzer): Boolean {
    val elements = analyzer.elements
    val parent = ExtractMethodHelper.getValidParentOf(elements.first())
    val holderClass = PsiTreeUtil.getNonStrictParentOfType(parent, PsiClass::class.java) ?: return false
    val method = PsiTreeUtil.getNonStrictParentOfType(parent, PsiMethod::class.java) ?: return false
    val firstStatement = method.body?.statements?.firstOrNull() ?: return false
    val startsOnBegin = firstStatement.textRange in TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
    val outStatements = method.body?.statements.orEmpty().dropWhile { it.textRange.endOffset <= elements.last().textRange.endOffset }
    val hasOuterFinalFieldAssignments = analyzer.findLocalFieldUsages(holderClass, outStatements)
                                      .any { fieldUsage -> fieldUsage.isWrite && fieldUsage.field.hasExplicitModifier(PsiModifier.FINAL) }
    return method.isConstructor && startsOnBegin && !hasOuterFinalFieldAssignments && analyzer.findOutputVariables().isEmpty()
  }

  private val annotationsToKeep: Set<String> = setOf(
    NLS, NON_NLS, LANGUAGE, PROPERTY_KEY, PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, "org.intellij.lang.annotations.RegExp",
    "org.intellij.lang.annotations.Pattern", "org.intellij.lang.annotations.MagicConstant", "org.intellij.lang.annotations.Subst",
    "org.intellij.lang.annotations.PrintFormat", "java.util.regex.Pattern"
  )

  private fun findAnnotationsToKeep(variable: PsiVariable?): List<PsiAnnotation> {
    return variable?.annotations.orEmpty().filter { it.qualifiedName in annotationsToKeep }
  }

  private fun findAnnotationsToKeep(reference: PsiReference?): List<PsiAnnotation> {
    return findAnnotationsToKeep(reference?.resolve() as? PsiVariable)
  }

  private fun withFilteredAnnotations(type: PsiType): PsiType {
    val project = type.annotations.firstOrNull()?.project ?: return type
    val factory = PsiElementFactory.getInstance(project)
    val typeHolder = factory.createParameter("x", type)
    typeHolder.type.annotations.filterNot { it.qualifiedName in annotationsToKeep }.forEach { it.delete() }
    return typeHolder.type
  }

  private fun withFilteredAnnotations(inputParameter: InputParameter): InputParameter {
    return inputParameter.copy(
      annotations = findAnnotationsToKeep(inputParameter.references.firstOrNull() as? PsiReference),
      type = withFilteredAnnotations(inputParameter.type)
    )
  }

  private fun withFilteredAnnotation(output: DataOutput): DataOutput {
    val filteredType = withFilteredAnnotations(output.type)
    return when(output) {
      is VariableOutput -> output.copy(annotations = findAnnotationsToKeep(output.variable), type = filteredType)
      is ExpressionOutput -> output.copy(annotations = findAnnotationsToKeep(output.returnExpressions.singleOrNull() as? PsiReference), type = filteredType)
      is EmptyOutput, ArtificialBooleanOutput -> output
    }
  }

  fun withFilteredAnnotations(extractOptions: ExtractOptions): ExtractOptions {
    return extractOptions.copy(
      inputParameters = extractOptions.inputParameters.map { withFilteredAnnotations(it) },
      disabledParameters = extractOptions.disabledParameters.map { withFilteredAnnotations(it) },
      dataOutput = withFilteredAnnotation(extractOptions.dataOutput)
    )
  }
}