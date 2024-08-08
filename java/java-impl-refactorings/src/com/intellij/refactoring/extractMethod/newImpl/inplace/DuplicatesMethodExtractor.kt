// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.find.FindManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.TextRange
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.childLeafs
import com.intellij.refactoring.JavaRefactoringSettings
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.SignatureSuggesterPreviewDialog
import com.intellij.refactoring.extractMethod.newImpl.*
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.inputParameterOf
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.replacePsiRange
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.replaceWithMethod
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.createGreedyRangeMarker
import com.intellij.refactoring.extractMethod.newImpl.inplace.InplaceExtractUtils.textRangeOf
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.introduceField.ElementToWorkOn
import com.intellij.refactoring.util.duplicates.DuplicatesImpl
import com.intellij.ui.ReplacePromptDialog
import com.siyeh.ig.psiutils.SideEffectChecker.mayHaveSideEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DuplicatesMethodExtractor(val extractOptions: ExtractOptions, val targetClass: PsiClass, val rangeToReplace: RangeMarker) {

  val rangeToReplaceOriginal = rangeToReplace.textRange

  internal fun getElements(): List<PsiElement> {
    val file = targetClass.containingFile
    val range = rangeToReplace.textRange
    require(rangeToReplace.isValid)
    return ExtractSelector().suggestElementsToExtract(file, range)
  }

  companion object {
    private val isSilentMode = ApplicationManager.getApplication().isUnitTestMode
    var changeSignatureDefault: Boolean? = true.takeIf { isSilentMode }
    var replaceDuplicatesDefault: Boolean? = true.takeIf { isSilentMode }

    fun create(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): DuplicatesMethodExtractor {
      val file = targetClass.containingFile
      val document = file.viewProvider.document ?: throw IllegalStateException()
      val rangeToReplace  = createGreedyRangeMarker(document, textRangeOf(elements.first(), elements.last()))
      JavaDuplicatesFinder.linkCopiedClassMembersWithOrigin(file)
      val copiedFile = file.copy() as PsiFile
      val copiedClass = PsiTreeUtil.findSameElementInCopy(targetClass, copiedFile)
      val expression = elements.singleOrNull() as? PsiExpression
      val virtualExpressionRange = expression?.getUserData(ElementToWorkOn.TEXT_RANGE)?.textRange
      val range = virtualExpressionRange ?: TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
      val copiedElements = ExtractSelector().suggestElementsToExtract(copiedFile, range)
      val extractOptions = findExtractOptions(copiedClass, copiedElements, methodName, makeStatic)
      return DuplicatesMethodExtractor(extractOptions, targetClass, rangeToReplace)
    }
  }

  suspend fun extract(): ExtractedElements {
    val file = readAction { targetClass.containingFile }
    val project = file.project
    val elements = readAction { getElements() }

    val preparedElements = readAction { MethodExtractor().prepareRefactoringElements(extractOptions) }
    val (callsPointer, methodPointer) = writeCommandAction(project, ExtractMethodHandler.getRefactoringName()) {
      val (calls, method) = replaceWithMethod(targetClass, elements, preparedElements)
      val methodPointer = SmartPointerManager.createPointer(method)
      val callsPointer = calls.map(SmartPointerManager::createPointer)
      Pair(callsPointer, methodPointer)
    }
    val replacedMethod = constrainedReadAction(ReadConstraint.withDocumentsCommitted(file.project)) {
      methodPointer.element ?: throw IllegalStateException()
    }
    val replacedCalls = constrainedReadAction(ReadConstraint.withDocumentsCommitted(file.project)) {
      callsPointer.map { it.element ?: throw IllegalStateException() }
    }

    return ExtractedElements(replacedCalls, replacedMethod)
  }

  suspend fun replaceDuplicates(editor: Editor, method: PsiMethod, beforeDuplicateReplaced: (candidate: List<PsiElement>) -> Unit = {}) {
    val project = readAction { editor.project } ?: return
    val calls = readAction { getElements() }
    if (calls.isEmpty()) return
    val defaultExtraction = ExtractedElements(calls, method)

    val prepareTimeStart = System.currentTimeMillis()

    val searchTitle = JavaRefactoringBundle.message("extract.method.progress.search.duplicates")
    val (exactReplacement, parametrizedReplacement) = withBackgroundProgress(project, searchTitle) {
      readAction {
        val (exactDuplicates, parametrizedDuplicates) = findDuplicates(method, calls)
        val exactReplacement = ReplaceDescriptor(exactDuplicates, defaultExtraction, extractOptions.inputParameters)
        val parametrizedReplacement = findParametrizedDescriptor(extractOptions.copy(methodName = method.name), parametrizedDuplicates)
        exactReplacement to parametrizedReplacement
      }
    }

    val prepareTimeEnd = System.currentTimeMillis()
    InplaceExtractMethodCollector.duplicatesSearched.log(prepareTimeEnd - prepareTimeStart)

    val replacement = withContext(Dispatchers.EDT) {
      val shouldChangeSignature = askToChangeSignature(exactReplacement, parametrizedReplacement)
      val chosenReplacement = if (shouldChangeSignature) parametrizedReplacement else exactReplacement
      val confirmedDuplicates = confirmDuplicates(editor, chosenReplacement.duplicates)
       chosenReplacement.copy(duplicates = confirmedDuplicates)
    }

    replacement.duplicates.forEach { beforeDuplicateReplaced(it.candidate) }

    replaceDuplicates(replacement, method, calls)
  }

  private suspend fun replaceDuplicates(replacement: ReplaceDescriptor, method: PsiMethod, calls: List<PsiElement>) {
    val project = readAction { method.project }
    val replaceTitle = JavaRefactoringBundle.message("extract.method.progress.replace.duplicates")
    val duplicatesExtractOptions = withBackgroundProgress(project, replaceTitle) {
      readAction {
        replacement.duplicates.map { duplicate -> createExtractDescriptor(duplicate, replacement.parameters) }
      }
    }

    writeCommandAction(project, ExtractMethodHandler.getRefactoringName()) {
      if (duplicatesExtractOptions.any { options -> options.isStatic }) {
        replacement.elements.method.modifierList.setModifierProperty(PsiModifier.STATIC, true)
      }

      replacePsiRange(calls, replacement.elements.callElements)
      val replacedMethod = method.replace(replacement.elements.method) as PsiMethod

      replacement.duplicates.zip(duplicatesExtractOptions).forEach { (duplicate, extractOptions) ->
        val callElements = CallBuilder(extractOptions.elements.first()).createCall(replacedMethod, extractOptions)
        replacePsiRange(duplicate.candidate, callElements)
      }
    }
  }

  private fun findDuplicates(method: PsiMethod, calls: List<PsiElement>): DuplicatesSearchResult {
    val searchScope = method.containingClass ?: method.containingFile
    val parameterExpressions = extractOptions.inputParameters.flatMap { parameter -> parameter.references }.toSet()
    val finder = JavaDuplicatesFinder(extractOptions.elements).withParametrizedExpressions(parameterExpressions)
    var duplicates = finder
      .findDuplicates(searchScope)
      .filterNot { duplicate ->
        findParentMethod(duplicate.candidate.first()) == method || areElementsIntersected(duplicate.candidate, calls)
      }

    val initialParameters = extractOptions.inputParameters.flatMap(InputParameter::references).toSet()
    val exactDuplicates = duplicates.filter { duplicate ->
      duplicate.parametrizedExpressions.all { changedExpression -> changedExpression.pattern in initialParameters }
    }

    val parametrizedExpressions = duplicates.flatMap { it.parametrizedExpressions.map(ParametrizedExpression::pattern) }
    val duplicatesFinder = finder.withParametrizedExpressions(parametrizedExpressions.toSet())

    val parametrizedDuplicates = duplicates.mapNotNull { duplicatesFinder.createDuplicateIfPossible(it.pattern, it.candidate) }
    return DuplicatesSearchResult(exactDuplicates, parametrizedDuplicates)
  }

  private fun findParametrizedDescriptor(defaultExtractOptions: ExtractOptions, parametrizedDuplicates: List<Duplicate>): ReplaceDescriptor {
    val updatedParameters: List<InputParameter> = findNewParameters(defaultExtractOptions.inputParameters, parametrizedDuplicates)
    val parametrizedExtraction = MethodExtractor().prepareRefactoringElements(defaultExtractOptions.copy(inputParameters = updatedParameters))
    return ReplaceDescriptor(parametrizedDuplicates, parametrizedExtraction, updatedParameters)
  }

  private data class ReplaceDescriptor(
    val duplicates: List<Duplicate>,
    val elements: ExtractedElements,
    val parameters: List<InputParameter>
  )

  private data class DuplicatesSearchResult(val exactDuplicates: List<Duplicate>, val parametrizedDuplicates: List<Duplicate>)

  private fun askToChangeSignature(exactReplacement: ReplaceDescriptor, parametrizedReplacement: ReplaceDescriptor): Boolean {
    val exactDuplicates = exactReplacement.duplicates.size
    val parametrizedDuplicates = parametrizedReplacement.duplicates.size
    if (parametrizedDuplicates <= exactDuplicates) return false
    if (!isGoodSignatureChange(exactReplacement.elements, parametrizedReplacement.elements)) return false
    val defaultResponse = changeSignatureDefault
    if (defaultResponse != null) return defaultResponse

    val oldMethodCall = findMethodCallInside(exactReplacement.elements.callElements.firstOrNull())
    val newMethodCall = findMethodCallInside(parametrizedReplacement.elements.callElements.firstOrNull())
    if (oldMethodCall == null || newMethodCall == null) return false
    val manager = CodeStyleManager.getInstance(exactReplacement.elements.method.project)
    val initialMethod = manager.reformat(exactReplacement.elements.method.copy()) as PsiMethod
    val parametrizedMethod = manager.reformat(parametrizedReplacement.elements.method) as PsiMethod

    val dialog = SignatureSuggesterPreviewDialog(initialMethod, parametrizedMethod, oldMethodCall,
                                                 newMethodCall, exactDuplicates, parametrizedDuplicates - exactDuplicates)
    return dialog.showAndGet()
  }

  private fun createExtractDescriptor(duplicate: Duplicate, parameters: List<InputParameter>): ExtractOptions {
    val expressionMap = duplicate.parametrizedExpressions.associate { (pattern, candidate) -> pattern to candidate }
    fun getMappedParameter(parameter: InputParameter): InputParameter {
      val references = parameter.references.map { expression -> expressionMap[expression] ?: expression }
      return parameter.copy(references = references)
    }

    return findExtractOptions(duplicate.candidate).copy(inputParameters = parameters.map(::getMappedParameter))
  }

  private fun isGoodSignatureChange(exactExtractElements: ExtractedElements, parametrizedExtractElements: ExtractedElements): Boolean {
    val parametersSizeBefore = exactExtractElements.method.parameterList.parameters.size
    val parametersSizeAfter = parametrizedExtractElements.method.parameterList.parameters.size
    val codeSizeBefore = extractOptions.elements.sumOf(::countNonEmptyLeafs)
    val callSizeAfter = parametrizedExtractElements.callElements.sumOf(::countNonEmptyLeafs)
    val addedParameters = parametersSizeAfter - parametersSizeBefore
    if (addedParameters <= 0) {
      // do not require reduce in call size if we just replace parameter
      return callSizeAfter <= codeSizeBefore
    }
    else if (addedParameters <= 3) {
      // require significant reduce in call if we introduce new parameters
      return 1.75 * callSizeAfter < codeSizeBefore && parametersSizeAfter <= 5
    }
    else {
      return false
    }
  }

  private fun countNonEmptyLeafs(element: PsiElement): Int {
    return element.childLeafs().filter { leaf -> leaf.text.isNotBlank() }.count()
  }

  private fun findMethodCallInside(element: PsiElement?): PsiMethodCallExpression? {
    return PsiTreeUtil.findChildOfType(element, PsiMethodCallExpression::class.java, false)
  }

  private fun areElementsIntersected(firstElements: List<PsiElement>, secondElements: List<PsiElement>): Boolean {
    if (firstElements.isEmpty() || secondElements.isEmpty()) {
      return false
    }
    if (firstElements.first().containingFile != secondElements.first().containingFile) {
      return false
    }
    val firstRange = TextRange(firstElements.first().textRange.startOffset, firstElements.last().textRange.endOffset)
    val secondRange = TextRange(secondElements.first().textRange.startOffset, secondElements.last().textRange.endOffset)
    return firstRange.intersects(secondRange)
  }

  private fun findNewParameters(parameters: List<InputParameter>, duplicates: List<Duplicate>): List<InputParameter> {
    if (duplicates.isEmpty()) return parameters
    val updatedParameters = duplicates.fold(parameters) { updatedParameters, duplicate ->
      updateParameters(updatedParameters, duplicate.parametrizedExpressions)
    }
    return ExtractMethodPipeline.foldParameters(updatedParameters, LocalSearchScope(duplicates.first().pattern.toTypedArray()))
  }

  private fun confirmDuplicates(editor: Editor, duplicates: List<Duplicate>): List<Duplicate> {
    if (replaceDuplicatesDefault == true) return duplicates
    if (replaceDuplicatesDefault == false) return emptyList()
    val project = editor.project ?: return emptyList()
    if (duplicates.isEmpty()) return duplicates
    val initialPosition = editor.caretModel.logicalPosition
    val confirmedDuplicates = mutableListOf<Duplicate>()
    duplicates.forEach { duplicate ->
      val highlighters = DuplicatesImpl.previewMatch(project, editor, textRangeOf(duplicate.candidate.first(), duplicate.candidate.last()))
      try {
        val prompt = ReplacePromptDialog(false, JavaRefactoringBundle.message("process.duplicates.title"), project)
        prompt.show()
        when (prompt.exitCode) {
          FindManager.PromptResult.OK -> confirmedDuplicates.add(duplicate)
          FindManager.PromptResult.ALL -> return duplicates
          FindManager.PromptResult.CANCEL -> return emptyList()
        }
      } finally {
        highlighters.forEach { highlighter -> HighlightManager.getInstance(project).removeSegmentHighlighter(editor, highlighter) }
      }
    }
    editor.scrollingModel.scrollTo(initialPosition, ScrollType.MAKE_VISIBLE)
    return confirmedDuplicates
  }

  private fun updateParameters(parameters: List<InputParameter>, changes: List<ParametrizedExpression>): List<InputParameter> {
    val changeMap = changes.associate { (pattern, candidate) -> pattern to candidate }
    val expressionGroups = groupEquivalentExpressions(changes.map(ParametrizedExpression::pattern))
    val parametersGroupedByPatternExpressions = expressionGroups.map { expressionGroup ->
      val parameter = parameters.firstOrNull { expressions -> isEqual(expressionGroup.first(), expressions.references.first()) }
      parameter?.copy(references = expressionGroup) ?: inputParameterOf(expressionGroup)
    }
    val splitParameters = parametersGroupedByPatternExpressions.flatMap { parameter -> splitByCandidateExpressions(parameter, parameter.references.map { changeMap[it]!! }) }
    return fixNameConflicts(splitParameters)
  }

  private fun fixNameConflicts(parameters: List<InputParameter>): List<InputParameter> {
    val reservedNames = mutableMapOf<String, Int>()
    return parameters.map {
      val nextIndex = reservedNames[it.name]
      reservedNames[it.name] = (nextIndex ?: 0) + 1
      val name = if (nextIndex == null) it.name else "${it.name}$nextIndex"
      it.copy(name = name)
    }
  }

  private fun splitByCandidateExpressions(parameter: InputParameter, candidateExpressions: List<PsiExpression>): List<InputParameter> {
    val map = candidateExpressions.zip(parameter.references).associate { it.first to it.second }
    val expressionGroups = groupEquivalentExpressions(candidateExpressions)
    return expressionGroups.map { expressionGroup -> parameter.copy(references = expressionGroup.map { map[it]!! }) }
  }

  private fun groupEquivalentExpressions(expressions: List<PsiExpression>): List<List<PsiExpression>> {
    val groups = mutableListOf<MutableList<PsiExpression>>()
    expressions.forEach { expression ->
      val group = groups.firstOrNull { group -> isEqual(expression, group.first()) }
      if (group != null) {
        group.add(expression)
      } else {
        groups.add(mutableListOf(expression))
      }
    }
    return groups.sortedBy { group: List<PsiExpression> -> group.minOf { expression -> expression.textRange.startOffset } }
  }

  private fun isEqual(first: PsiExpression, second: PsiExpression): Boolean {
    return PsiEquivalenceUtil.areElementsEquivalent(first, second) && !mayHaveSideEffects(first) && !mayHaveSideEffects(second)
  }

  private fun findParentMethod(element: PsiElement) = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
}

private fun findExtractOptions(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): ExtractOptions {
  val analyzer = CodeFragmentAnalyzer(elements)
  var options = findExtractOptions(elements).copy(methodName = methodName)
  options = ExtractMethodPipeline.withTargetClass(analyzer, options, targetClass) ?: throw IllegalStateException(
    "Failed to set target class")
  options = if (makeStatic) ExtractMethodPipeline.withForcedStatic(analyzer, options)!! else options
  return options
}

fun DuplicatesMethodExtractor.extractInDialog() {
  val dialog = ExtractMethodDialogUtil.createDialog(extractOptions)
  if (!dialog.showAndGet()) return
  val dialogOptions = ExtractMethodPipeline.withDialogParameters(extractOptions, dialog)
  val passFieldsAsParameters = extractOptions.inputParameters.size != dialogOptions.inputParameters.size
  if (!passFieldsAsParameters) {
    JavaRefactoringSettings.getInstance().EXTRACT_STATIC_METHOD = dialogOptions.isStatic
  }
  val mappedExtractor = DuplicatesMethodExtractor(dialogOptions, targetClass, rangeToReplace)
  //todo avoid blocking, suspend should be propagated further
  val (_, method) = runWithModalProgressBlocking(extractOptions.project, ExtractMethodHandler.getRefactoringName()) {
    mappedExtractor.extract()
  }
  MethodExtractor.sendRefactoringDoneEvent(method)
  val editor = PsiEditorUtil.findEditor(targetClass) ?: return
  runWithModalProgressBlocking(extractOptions.project, ExtractMethodHandler.getRefactoringName()) {
    mappedExtractor.replaceDuplicates(editor, method)
  }
}