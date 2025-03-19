// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.AnnotationUtil.*
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil
import com.intellij.codeInsight.daemon.impl.quickfix.AnonymousTargetClassPreselectionUtil
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.JavaFeature
import com.intellij.psi.*
import com.intellij.psi.formatter.java.MultipleFieldDeclarationHelper
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.extractMethod.ExtractMethodDialog
import com.intellij.refactoring.extractMethod.ParametersFolder
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.areSame
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.findRequiredTypeParameters
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.inputParameterOf
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.RefactoringUtil
import com.intellij.refactoring.util.VariableData
import com.siyeh.ig.psiutils.ClassUtils
import java.util.concurrent.CompletableFuture

object ExtractMethodPipeline {

  fun withDialogParameters(extractOptions: ExtractOptions, extractDialog: ExtractMethodDialog): ExtractOptions {
    val analyzer = CodeFragmentAnalyzer(extractOptions.elements)
    var options = withMappedName(extractOptions, extractDialog.chosenMethodName)
    if (extractDialog.isMakeStatic && !options.isStatic) {
      options = withForcedStatic(analyzer, options) ?: options
    }
    options = withMappedParametersInput(options, extractDialog.chosenParameters.toList())
    options = options.copy(visibility = extractDialog.visibility)

    if (extractDialog.isChainedConstructor) {
      options = asConstructor(analyzer, options) ?: options
    } else {
      options = options.copy(dataOutput = extractOptions.dataOutput.withType(extractDialog.returnType))
    }
    return options
  }

  fun withTargetClass(analyzer: CodeFragmentAnalyzer, extractOptions: ExtractOptions, targetClass: PsiClass): ExtractOptions? {
    if (targetClass.isInterface && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, targetClass)) return null
    if (extractOptions.targetClass == targetClass) return extractOptions
    val sourceMember = PsiTreeUtil.getContextOfType(extractOptions.elements.first(), PsiMember::class.java)
    val targetMember = generateSequence (sourceMember) { PsiTreeUtil.getParentOfType(it, PsiMember::class.java) }.find { member -> member.containingClass == targetClass }
    if (sourceMember == null || targetMember == null) return null

    val typeParameters = findRequiredTypeParameters(targetClass, extractOptions.elements)

    val additionalReferences = analyzer.findOuterLocals(sourceMember, targetMember) ?: return null
    val additionalParameters = additionalReferences.map { inputParameterOf(it) }
    val options = extractOptions.copy(
      targetClass = targetClass,
      inputParameters = extractOptions.inputParameters + additionalParameters,
      typeParameters = typeParameters
    )
    return withDefaultStatic(options)
  }

  fun addMethodInBestPlace(targetClass: PsiClass, place: PsiElement, method: PsiMethod): PsiMethod {
    val anchorElement = findPlaceToPutMethod(targetClass, place)
    val element = anchorElement?.addSiblingAfter(method) ?: targetClass.add(method)
    return element as PsiMethod
  }

  private fun findPlaceToPutMethod(targetClass: PsiClass, place: PsiElement): PsiMember? {
    fun getMemberContext(element: PsiElement) = PsiTreeUtil.getContextOfType(element, PsiMember::class.java)
    val anchorElement = generateSequence (getMemberContext(place), ::getMemberContext)
      .takeWhile { context -> context != targetClass }
      .lastOrNull()
    if (anchorElement is PsiField) {
      val lastFieldInGroup = MultipleFieldDeclarationHelper.findLastFieldInGroup(anchorElement.node).psi
      return lastFieldInGroup as? PsiField ?: anchorElement
    }
    return anchorElement
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

  private fun withMappedParametersInput(extractOptions: ExtractOptions, variablesData: List<VariableData>): ExtractOptions {
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

  private fun withMappedName(extractOptions: ExtractOptions, methodName: String) = if (extractOptions.isConstructor) extractOptions else extractOptions.copy(methodName = methodName)

  fun withDefaultStatic(extractOptions: ExtractOptions): ExtractOptions {
    val expression = extractOptions.elements.singleOrNull() as? PsiExpression
    val statement = PsiTreeUtil.getParentOfType(expression, PsiExpressionStatement::class.java)
    if (statement != null && JavaHighlightUtil.isSuperOrThisCall(statement, true, true)) {
      return extractOptions.copy(isStatic = true)
    }
    val shouldBeStatic = PsiUtil.getEnclosingStaticElement(extractOptions.elements.first(), extractOptions.targetClass) != null
    return extractOptions.copy(isStatic = shouldBeStatic)
  }

  private fun findDefaultTargetCandidate(candidates: List<PsiClass>): PsiClass {
    return AnonymousTargetClassPreselectionUtil.getPreselection(candidates, candidates.first()) ?: candidates.first()
  }

  fun findAllOptionsToExtract(elements: List<PsiElement>): List<ExtractOptions> {
    val extractOptions = findExtractOptions(elements)
    val analyzer = CodeFragmentAnalyzer(extractOptions.elements)
    return generateSequence (extractOptions.targetClass as PsiElement) { it.parent }
      .takeWhile { it !is PsiFile }
      .filterIsInstance<PsiClass>()
      .mapNotNull { targetClass -> withTargetClass(analyzer, extractOptions, targetClass) }
      .toList()
  }

  fun selectOptionWithTargetClass(editor: Editor, project: Project, options: List<ExtractOptions>): CompletableFuture<ExtractOptions> {
    require(options.isNotEmpty())
    if (options.size == 1) {
      return CompletableFuture.completedFuture(options.first())
    }
    val classToOptionMap: Map<PsiClass, ExtractOptions> = options.associateBy { option -> option.targetClass }
    val selectedOption: CompletableFuture<ExtractOptions> = CompletableFuture()
    val processor = PsiElementProcessor<PsiClass> { selected ->
      selectedOption.complete(classToOptionMap[selected])
      true
    }

    val preselection = findDefaultTargetCandidate(classToOptionMap.keys.toList())
    PsiTargetNavigator(classToOptionMap.keys.toTypedArray()).selection(preselection)
      .createPopup(project,
                          RefactoringBundle.message("choose.destination.class"), processor)
      .showInBestPositionFor(editor)

    return selectedOption
  }

  private fun getSimpleArrayAccess(arrayReference: PsiElement): PsiExpression? {
    val arrayAccess = PsiTreeUtil.getParentOfType(arrayReference, PsiArrayAccessExpression::class.java)
    return arrayAccess?.takeIf { !RefactoringUtil.isAssignmentLHS(arrayAccess)
                                 && arrayAccess.arrayExpression == arrayReference && arrayAccess.indexExpression is PsiReference }
  }

  private fun findAllArrayAccesses(inputParameter: InputParameter): List<PsiExpression>? {
    return inputParameter.references.map { reference -> getSimpleArrayAccess(reference) ?: return null }
  }

  private fun isInsideParentGroup(parents: List<PsiElement>, children: List<PsiElement>): Boolean {
    return children.all { child -> parents.any { parent -> PsiTreeUtil.isAncestor(parent, child, false) } }
  }

  fun foldParameters(parameters: List<InputParameter>, scope: LocalSearchScope): List<InputParameter> {
    val foldableArrayGroups: List<List<PsiExpression>> = parameters
      .mapNotNull { parameter -> findAllArrayAccesses(parameter) }
      .filter { arrayAccesses -> isSafeToFoldArrayAccesses(arrayAccesses, scope) }
    val groupParameters: Map<List<PsiExpression>, List<InputParameter>> = foldableArrayGroups
      .associateWith { arrayGroup -> parameters.filter { parameter -> isInsideParentGroup(arrayGroup, parameter.references) } }
      .filter { (_, coveredParameters) -> coveredParameters.size >= 2 }
    val coveredParameters = groupParameters.values.flatten().toSet()
    val newParameters = groupParameters.keys.map { foldableGroup -> inputParameterOf(foldableGroup) }
    return parameters - coveredParameters + newParameters
  }

  private fun isSafeToFoldArrayAccesses(arrayAccesses: List<PsiExpression>, scope: LocalSearchScope): Boolean {
    return areSame(arrayAccesses) && arrayAccesses.all { expression -> ParametersFolder.isSafeToFoldArrayAccess(scope, expression) }
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
    val targetClass = extractOptions.targetClass
    val isInnerClass = PsiUtil.isLocalOrAnonymousClass(targetClass) || PsiUtil.isInnerClass(targetClass)
    if (isInnerClass && !PsiUtil.isAvailable(JavaFeature.INNER_STATICS, targetClass)) return null
    val memberUsages = analyzer.findInstanceMemberUsages(targetClass, extractOptions.elements)
    if (memberUsages.any(::isNotExtractableUsage)) return null
    val memberParameters = memberUsages.groupBy(MemberUsage::member).entries
      .map { (member: PsiMember, usages: List<MemberUsage>) ->
        createInputParameter(member, usages.map(MemberUsage::reference)) ?: return null
      }
    val topmostClass = PsiTreeUtil.getTopmostParentOfType(targetClass, PsiMember::class.java) ?: targetClass
    val localParameters = analyzer.findOuterLocals(targetClass, topmostClass)?.map(::inputParameterOf) ?: return null
    val addedParameters = memberParameters + localParameters
    return extractOptions.copy(
      inputParameters = extractOptions.inputParameters + addedParameters,
      isStatic = true,
      typeParameters = findRequiredTypeParameters(null, extractOptions.elements)
    )
  }

  private fun isNotExtractableUsage(usage: MemberUsage): Boolean {
    return PsiUtil.isAccessedForWriting(usage.reference) || (usage.member is PsiClass && ClassUtils.isNonStaticClass(usage.member))
  }

  private fun createInputParameter(member: PsiMember, usages: List<PsiExpression>): InputParameter? {
    val name = member.name ?: return null
    val type = when (member) {
      is PsiField -> member.type
      is PsiClass -> usages.firstOrNull()?.type ?: PsiTypesUtil.getClassType(member)
      else -> return null
    }
    return InputParameter(usages, StringUtil.decapitalize(name), type)
  }

  fun canBeConstructor(analyzer: CodeFragmentAnalyzer): Boolean {
    val elements = analyzer.elements
    val parent = ExtractMethodHelper.getValidParentOf(elements.first())
    val holderClass = PsiTreeUtil.getNonStrictParentOfType(parent, PsiClass::class.java) ?: return false
    val method = PsiTreeUtil.getNonStrictParentOfType(parent, PsiMethod::class.java) ?: return false
    val firstStatement = method.body?.statements?.firstOrNull() ?: return false
    val startsOnBegin = firstStatement.textRange in TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
    val outStatements = method.body?.statements.orEmpty().dropWhile { it.textRange.endOffset <= elements.last().textRange.endOffset }
    val hasOuterFinalFieldAssignments = analyzer.findInstanceMemberUsages(holderClass, outStatements)
      .any { localUsage -> localUsage.member.hasModifierProperty(PsiModifier.FINAL) && PsiUtil.isAccessedForWriting(localUsage.reference) }
    return method.isConstructor && startsOnBegin && !hasOuterFinalFieldAssignments && analyzer.findOutputVariables().isEmpty()
  }

  private val annotationsToKeep: Set<String> = setOf(
    NLS, NON_NLS, LANGUAGE, PROPERTY_KEY, PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, "org.intellij.lang.annotations.RegExp",
    "org.intellij.lang.annotations.Pattern", "org.intellij.lang.annotations.MagicConstant", "org.intellij.lang.annotations.Subst",
    "org.intellij.lang.annotations.PrintFormat"
  )

  private fun findAnnotationsToKeep(variable: PsiVariable?): List<PsiAnnotation> {
    return variable?.annotations.orEmpty().filter { it.qualifiedName in annotationsToKeep }
  }

  private fun findAnnotationsToKeep(reference: PsiReference?): List<PsiAnnotation> {
    return findAnnotationsToKeep(reference?.resolve() as? PsiVariable)
  }

  private fun withFilteredAnnotations(type: PsiType, context: PsiElement?): PsiType {
    val project = type.annotations.firstOrNull()?.project ?: return type
    val factory = PsiElementFactory.getInstance(project)
    val typeHolder = factory.createParameter("x", type, context)
    typeHolder.type.annotations.filterNot { it.qualifiedName in annotationsToKeep }.forEach { it.delete() }
    return typeHolder.type
  }

  private fun withFilteredAnnotations(inputParameter: InputParameter, context: PsiElement?): InputParameter {
    return inputParameter.copy(
      annotations = findAnnotationsToKeep(inputParameter.references.firstOrNull() as? PsiReference),
      type = withFilteredAnnotations(inputParameter.type, context)
    )
  }

  private fun withFilteredAnnotation(output: DataOutput, context: PsiElement?): DataOutput {
    val filteredType = withFilteredAnnotations(output.type, context)
    return when(output) {
      is VariableOutput -> output.copy(annotations = findAnnotationsToKeep(output.variable), type = filteredType)
      is ExpressionOutput -> output.copy(annotations = findAnnotationsToKeep(output.returnExpressions.singleOrNull() as? PsiReference), type = filteredType)
      is EmptyOutput, ArtificialBooleanOutput -> output
    }
  }

  fun withFilteredAnnotations(extractOptions: ExtractOptions): ExtractOptions {
    return extractOptions.copy(
      inputParameters = extractOptions.inputParameters.map { withFilteredAnnotations(it, extractOptions.targetClass) },
      disabledParameters = extractOptions.disabledParameters.map { withFilteredAnnotations(it, extractOptions.targetClass) },
      dataOutput = withFilteredAnnotation(extractOptions.dataOutput, extractOptions.targetClass)
    )
  }
}