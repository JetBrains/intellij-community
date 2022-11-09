// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.find.FindManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.SignatureSuggesterPreviewDialog
import com.intellij.refactoring.extractMethod.newImpl.*
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.inputParameterOf
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.replacePsiRange
import com.intellij.refactoring.extractMethod.newImpl.JavaDuplicatesFinder.Companion.textRangeOf
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.duplicates.DuplicatesImpl
import com.intellij.ui.ReplacePromptDialog
import com.siyeh.ig.psiutils.SideEffectChecker.mayHaveSideEffects

class DuplicatesMethodExtractor(val extractOptions: ExtractOptions, val anchor: PsiMember, val elements: List<PsiElement>) {

  companion object {
    private val isSilentMode = ApplicationManager.getApplication().isUnitTestMode
    var changeSignatureDefault: Boolean? = true.takeIf { isSilentMode }
    var replaceDuplicatesDefault: Boolean? = true.takeIf { isSilentMode }

    fun create(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): DuplicatesMethodExtractor {
      val file = targetClass.containingFile
      JavaDuplicatesFinder.linkCopiedClassMembersWithOrigin(file)
      val copiedFile = file.copy() as PsiFile
      val copiedClass = PsiTreeUtil.findSameElementInCopy(targetClass, copiedFile)
      val copiedElements = elements.map { PsiTreeUtil.findSameElementInCopy(it, copiedFile) }
      val extractOptions = findExtractOptions(copiedClass, copiedElements, methodName, makeStatic)
      val anchor = PsiTreeUtil.findSameElementInCopy(extractOptions.anchor, file)
      return DuplicatesMethodExtractor(extractOptions, anchor, elements)
    }
  }

  private var callsToReplace: List<SmartPsiElementPointer<PsiElement>>? = null

  fun extract(): ExtractedElements {
    val file = anchor.containingFile
    val document = file.viewProvider.document

    val elementsToReplace = MethodExtractor().prepareRefactoringElements(extractOptions)
    val (calls, method) = runWriteAction {
      val callElements = replacePsiRange(elements, elementsToReplace.callElements)
      val addedMethod = anchor.addSiblingAfter(elementsToReplace.method) as PsiMethod
      Pair(callElements, addedMethod)
    }

    val methodPointer = SmartPointerManager.createPointer(method)
    val callsPointer = calls.map(SmartPointerManager::createPointer)
    val manager = PsiDocumentManager.getInstance(file.project)
    manager.doPostponedOperationsAndUnblockDocument(document)
    manager.commitDocument(document)
    val replacedMethod = methodPointer.element ?: throw IllegalStateException()
    val replacedCalls = callsPointer.map { it.element ?: throw IllegalStateException() }

    this.callsToReplace = callsPointer

    return ExtractedElements(replacedCalls, replacedMethod)
  }

  fun replaceDuplicates(editor: Editor, method: PsiMethod) {
    val project = editor.project ?: return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val calls = callsToReplace?.map { it.element!! } ?: return
    val finder = JavaDuplicatesFinder(extractOptions.elements)
    var duplicates = finder
      .findDuplicates(method.containingClass ?: file)
      .filterNot { duplicate ->
        findParentMethod(duplicate.candidate.first()) == method || areElementsIntersected(duplicate.candidate, calls)
      }

    //TODO check same data output
    //TODO check same flow output (+ same return values)

    val parameterExpressions = extractOptions.inputParameters.flatMap { parameter -> parameter.references }
    val changedExpressions = duplicates.flatMap { it.changedExpressions.map(ChangedExpression::pattern) }
    val duplicatesFinder = finder.withPredefinedChanges((parameterExpressions + changedExpressions).toSet())

    val duplicatesWithUnifiedParameters = duplicates.mapNotNull { duplicatesFinder.createDuplicate(it.pattern, it.candidate) }

    val updatedParameters: List<InputParameter> = findNewParameters(extractOptions.inputParameters, duplicatesWithUnifiedParameters)

    val parametrizedExtraction = MethodExtractor().prepareRefactoringElements(
      extractOptions.copy(inputParameters = updatedParameters, methodName = method.name))

    //TODO clean up
    val initialParameters = extractOptions.inputParameters.flatMap(InputParameter::references).toSet()
    val exactDuplicates = duplicates.filter { duplicate ->
      duplicate.changedExpressions.all { changedExpression -> changedExpression.pattern in initialParameters }
    }
    val oldMethodCall = findMethodCallInside(calls.firstOrNull()) ?: throw IllegalStateException()
    val newMethodCall = findMethodCallInside(parametrizedExtraction.callElements.firstOrNull()) ?: throw IllegalStateException()
    val parametrizedDuplicatesNumber = duplicates.size - exactDuplicates.size
    fun confirmChangeSignature(): Boolean {
      val dialog = SignatureSuggesterPreviewDialog(method, parametrizedExtraction.method, oldMethodCall, newMethodCall, parametrizedDuplicatesNumber)
      return dialog.showAndGet()
    }
    val confirmChange: () -> Boolean = changeSignatureDefault?.let { default -> {default} } ?: ::confirmChangeSignature
    val isGoodSignatureChange = isGoodSignatureChange(extractOptions.elements, extractOptions.inputParameters,
                                                      parametrizedExtraction.callElements, updatedParameters)
    val changeSignature = parametrizedDuplicatesNumber > 0 && isGoodSignatureChange && confirmChange()
    duplicates = if (changeSignature) duplicatesWithUnifiedParameters else exactDuplicates
    val parameters = if (changeSignature) updatedParameters else extractOptions.inputParameters
    val extractedElements = if (changeSignature) parametrizedExtraction else ExtractedElements(calls, method)

    duplicates = when (replaceDuplicatesDefault) {
      null -> confirmDuplicates (project, editor, duplicates)
      true -> duplicates
      false -> emptyList()
    }

    val replacedMethod = runWriteAction {
      replacePsiRange(calls, extractedElements.callElements)
      method.replace(extractedElements.method) as PsiMethod
    }

    duplicates.forEach { duplicate ->
      val expressionMap = duplicate.changedExpressions.associate { (pattern, candidate) -> pattern to candidate }
      fun getMappedParameter(parameter: InputParameter): InputParameter {
        val references = parameter.references.map { expression -> expressionMap[expression] ?: expression }
        return parameter.copy(references = references)
      }

      val duplicateOptions = findExtractOptions(duplicate.candidate).copy(inputParameters = parameters.map(::getMappedParameter))
      val callElements = CallBuilder(duplicateOptions.elements.first()).createCall(replacedMethod, duplicateOptions)
      runWriteAction {
        replacePsiRange(duplicate.candidate, callElements)
      }
    }
  }

  private fun isGoodSignatureChange(callBefore: List<PsiElement>, initialParameters: List<InputParameter>,
                                    callAfter: List<PsiElement>, updatedParameters: List<InputParameter>): Boolean {
    val sizeAfter = callAfter.sumOf(::calculateCodeLeafs)
    val sizeBefore = callBefore.sumOf(::calculateCodeLeafs)
    val addedParameters = updatedParameters.size - initialParameters.size
    return 1.75 * sizeAfter < sizeBefore && addedParameters <= 3 && updatedParameters.size <= 5
  }

  private fun calculateCodeLeafs(element: PsiElement): Int {
    return SyntaxTraverser.psiTraverser(element)
      .filter { psiElement -> psiElement.firstChild == null && psiElement.text.isNotBlank() }
      .traverse()
      .count()
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
    return duplicates
      .fold(parameters) { updatedParameters, duplicate -> updateParameters(updatedParameters, duplicate.changedExpressions) }
  }

  private fun confirmDuplicates(project: Project, editor: Editor, duplicates: List<Duplicate>): List<Duplicate> {
    if (duplicates.isEmpty()) return duplicates
    val initialPosition = editor.caretModel.logicalPosition
    val confirmedDuplicates = mutableListOf<Duplicate>()
    duplicates.forEach { duplicate ->
      val highlighters = DuplicatesImpl.previewMatch(project, editor, textRangeOf(duplicate.candidate))
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

  private fun updateParameters(parameters: List<InputParameter>, changes: List<ChangedExpression>): List<InputParameter> {
    val changeMap = changes.associate { (pattern, candidate) -> pattern to candidate }
    val expressionGroups = groupEquivalentExpressions(changes.map(ChangedExpression::pattern))
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

fun extractInDialog(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean) {
  val extractor = DuplicatesMethodExtractor.create(targetClass, elements, methodName, makeStatic)
  val dialogOptions = MapFromDialog.mapFromDialog(extractor.extractOptions)
  if (dialogOptions != null) {
    val mappedExtractor = DuplicatesMethodExtractor(dialogOptions, extractor.anchor, extractor.elements)
    MethodExtractor().executeRefactoringCommand(targetClass.project) {
      MethodExtractor.sendRefactoringStartedEvent(elements.toTypedArray())
      val (callElements, method) = mappedExtractor.extract()
      MethodExtractor.sendRefactoringDoneEvent(method)
      val editor = PsiEditorUtil.findEditor(targetClass)
      if (editor != null) {
        mappedExtractor.replaceDuplicates(editor, method)
      }
    }
  }
}