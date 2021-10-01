// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.find.FindManager
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.SignatureSuggesterPreviewDialog
import com.intellij.refactoring.extractMethod.newImpl.*
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.guessName
import com.intellij.refactoring.extractMethod.newImpl.JavaDuplicatesFinder.Companion.textRangeOf
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import com.intellij.refactoring.util.duplicates.DuplicatesImpl
import com.intellij.ui.ReplacePromptDialog
import com.siyeh.ig.psiutils.SideEffectChecker.mayHaveSideEffects

class DuplicatesMethodExtractor: InplaceExtractMethodProvider {

  private var duplicatesFinder: JavaDuplicatesFinder? = null

  private var callsToReplace: List<SmartPsiElementPointer<PsiElement>>? = null

  private var extractOptions: ExtractOptions? = null

  override fun extract(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): Pair<PsiMethod, PsiMethodCallExpression> {
    val file = targetClass.containingFile
    val copiedFile = file.copy() as PsiFile
    val copiedClass = PsiTreeUtil.findSameElementInCopy(targetClass, copiedFile)
    val copiedElements = elements.map { PsiTreeUtil.findSameElementInCopy(it, copiedFile) }
    val extractOptions = findExtractOptions(copiedClass, copiedElements, methodName, makeStatic)
    val anchor = PsiTreeUtil.findSameElementInCopy(extractOptions.anchor, file)

    this.duplicatesFinder = JavaDuplicatesFinder(copiedElements)
    this.extractOptions = extractOptions

    val elementsToReplace = MethodExtractor().prepareRefactoringElements(extractOptions)
    val calls = MethodExtractor().replace(elements, elementsToReplace.callElements)
    val method = targetClass.addAfter(elementsToReplace.method, anchor) as PsiMethod

    this.callsToReplace = calls.map(SmartPointerManager::createPointer)
    val callExpression = PsiTreeUtil.findChildOfType(calls.first(), PsiMethodCallExpression::class.java, false)!!
    return Pair(method, callExpression)
  }

  override fun extractInDialog(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean) {
    val extractOptions = findExtractOptions(targetClass, elements, methodName, makeStatic)
    MethodExtractor().doDialogExtract(extractOptions)
  }

  private fun findExtractOptions(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): ExtractOptions {
    val analyzer = CodeFragmentAnalyzer(elements)
    var options = findExtractOptions(elements).copy(methodName = methodName)
    options = ExtractMethodPipeline.withTargetClass(analyzer, options, targetClass) ?: throw IllegalStateException("Failed to set target class")
    options = if (makeStatic) ExtractMethodPipeline.withForcedStatic(analyzer, options)!! else options
    return options
  }

  override fun postprocess(editor: Editor, method: PsiMethod) {
    val project = editor.project ?: return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
    val finder = duplicatesFinder ?: return
    val calls = callsToReplace?.map { it.element!! } ?: return
    val options = extractOptions ?: return
    var duplicates = finder.findDuplicates(method.containingClass ?: file)
      .filterNot { findParentMethod(it.candidate.first()) == method }
    //TODO check same data output
    //TODO check same flow output (+ same return values)

    val changes = duplicates.flatMap { it.changedExpressions.map(ChangedExpression::pattern) }.toSet()
    fun isEquivalent(pattern: PsiElement, candidate: PsiElement) = pattern !in changes && finder.isEquivalent(pattern, candidate)
    duplicates = duplicates.mapNotNull { finder.createDuplicate(it.pattern, it.candidate, ::isEquivalent) }
    val allParameters = duplicates
      .fold(options.inputParameters) { parameters, duplicate ->  updateParameters(parameters, duplicate.changedExpressions)}
    val elementsToReplace = MethodExtractor().prepareRefactoringElements(options.copy(inputParameters = allParameters, methodName = method.name))

    //TODO clean up
    val initialParameters = options.inputParameters.flatMap(InputParameter::references).toSet()
    val exactDuplicates = duplicates.filter {
      duplicate -> duplicate.changedExpressions.all { changedExpression -> changedExpression.pattern in initialParameters }
    }
    val oldMethodCall = PsiTreeUtil.findChildOfType(calls.first(), PsiMethodCallExpression::class.java)
    val newMethodCall = PsiTreeUtil.findChildOfType(elementsToReplace.callElements.first(), PsiMethodCallExpression::class.java)
    val parametrizedDuplicatesNumber = duplicates.size - exactDuplicates.size
    val changeSignature = SignatureSuggesterPreviewDialog(method, elementsToReplace.method, oldMethodCall, newMethodCall, parametrizedDuplicatesNumber).showAndGet()
    if (!changeSignature) {
      duplicates = exactDuplicates
    }

    duplicates = confirmDuplicates(project, editor, duplicates)
    if (duplicates.isEmpty()) return

    val replacedMethod = runWriteAction {
      MethodExtractor().replace(calls, elementsToReplace.callElements)
      method.replace(elementsToReplace.method) as PsiMethod
    }

    duplicates.forEach { duplicate ->
      val duplicateOptions = findExtractOptions(duplicate.candidate)
      val expressionMap = duplicate.changedExpressions.associate { (pattern, candidate) -> pattern to candidate }
      val duplicateParameters = allParameters.map { parameter -> parameter.copy(references = parameter.references.map { expression -> expressionMap[expression]!! }) }

      //TODO extract duplicate
      val builder = CallBuilder(duplicateOptions.project, duplicateOptions.elements.first())
      val call = builder.createMethodCall(replacedMethod, duplicateParameters.map { it.references.first() }).text
      val callElements = if (options.elements.singleOrNull() is PsiExpression) {
        builder.buildExpressionCall(call, duplicateOptions.dataOutput)
      } else {
        builder.buildCall(call, duplicateOptions.flowOutput, duplicateOptions.dataOutput, duplicateOptions.exposedLocalVariables)
      }
      runWriteAction {
        MethodExtractor().replace(duplicate.candidate, callElements)
      }
    }
  }

  private fun confirmDuplicates(project: Project, editor: Editor, duplicates: List<Duplicate>): List<Duplicate> {
    val confirmedDuplicates = mutableListOf<Duplicate>()
    duplicates.forEach { duplicate ->
      val highlighters = ArrayList<RangeHighlighter>()
      DuplicatesImpl.highlightMatch(project, editor, textRangeOf(duplicate.candidate), highlighters)
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
    return confirmedDuplicates
  }

  private fun updateParameters(parameters: List<InputParameter>, changes: List<ChangedExpression>): List<InputParameter> {
    val changeMap = changes.associate { (pattern, candidate) -> pattern to candidate }
    val expressionGroups = groupEquivalentExpressions(changes.map(ChangedExpression::pattern))
    val parametersGroupedByPatternExpressions = expressionGroups.map { expressionGroup ->
      val parameter = parameters.firstOrNull { expressions -> isEqual(expressionGroup.first(), expressions.references.first()) }
      parameter?.copy(references = expressionGroup) ?: InputParameter(expressionGroup, guessName(expressionGroup.first()), expressionGroup.first().type!!)
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
    return groups
  }

  private fun isEqual(first: PsiExpression, second: PsiExpression): Boolean {
    return PsiEquivalenceUtil.areElementsEquivalent(first, second) && !mayHaveSideEffects(first) && !mayHaveSideEffects(second)
  }

  private fun findParentMethod(element: PsiElement) = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
}