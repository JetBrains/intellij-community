// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.newImpl.*
import com.intellij.refactoring.extractMethod.newImpl.JavaDuplicatesFinder.Companion.textRangeOf
import com.intellij.refactoring.extractMethod.newImpl.structures.DataOutput
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter
import org.jetbrains.annotations.Nullable

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
    val duplicates = finder.findDuplicates(method.containingClass ?: file)
      .filterNot { findParentMethod(it.candidate.first()) == method }

    duplicates.forEach { duplicate ->
      printDuplicate(project, duplicate)
      val allParameters = createParametersFromExpressions(duplicate.changedExpressions)
      val duplicateOptions = findExtractOptions(duplicate.candidate)
      val expressionMap = duplicate.changedExpressions.associate { (pattern, candidate) -> pattern to candidate }
      val duplicateParameters = allParameters.map { parameter -> parameter.copy(references = parameter.references.map { expression -> expressionMap[expression]!! }) }

      val elementsToReplace = MethodExtractor().prepareRefactoringElements(options.copy(inputParameters = allParameters, methodName = method.name))
      val builder = CallBuilder(duplicateOptions.project, duplicateOptions.elements.first())
      runWriteAction {
        MethodExtractor().replace(calls, elementsToReplace.callElements)
        val replacedMethod = method.replace(elementsToReplace.method) as PsiMethod
        val call = builder.createMethodCall(replacedMethod, duplicateParameters.map { it.references.first() }).text
        val callElements = if (options.dataOutput is DataOutput.ExpressionOutput) {
          builder.buildExpressionCall(call, duplicateOptions.dataOutput)
        } else {
          builder.buildCall(call, duplicateOptions.flowOutput, duplicateOptions.dataOutput, duplicateOptions.exposedLocalVariables)
        }
        MethodExtractor().replace(duplicate.candidate, callElements)
      }
    }
  }

  private fun createParametersFromExpressions(changedExpressions: List<ChangedExpression>): List<InputParameter> {
    val parameters = changedExpressions.groupBy { "${it.pattern.text}:::${it.candidate.text}" }.values.map { InputParameter(it.map { it.pattern }, ExtractMethodHelper.guessName(it.first().pattern), it.first().pattern.type!!) }
    return parameters.groupBy { it.name }.values.flatMap { params -> params.mapIndexed { index, parameter ->  parameter.copy(name = "${parameter.name}${if (index != 0) index else ""}") } }
  }

  private fun printDuplicate(project: @Nullable Project, duplicate: Duplicate) {
    val document = PsiDocumentManager.getInstance(project).getDocument(duplicate.candidate.first().containingFile)!!
    println("Duplicate:")
    println(document.getText(textRangeOf(duplicate.candidate)))
    println()
    println("Changes:")
    duplicate.changedExpressions.forEach { (pattern, candidate) ->
      println("${pattern.text} ::: ${candidate.text}")
    }
  }

  private fun findParentMethod(element: PsiElement) = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
}