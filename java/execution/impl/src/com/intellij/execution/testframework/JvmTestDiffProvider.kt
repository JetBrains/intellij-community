// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionLineParserFactory
import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.uast.*

abstract class JvmTestDiffProvider : TestDiffProvider {
  abstract fun getExpectedElement(expression: UExpression, expected: String): PsiElement?

  override fun updateExpected(element: PsiElement, actual: String) {
    ElementManipulators.getManipulator(element)?.handleContentChange(element, actual)
  }

  final override fun findExpected(project: Project, stackTrace: String, expected: String): PsiElement? {
    val exceptionCache = ExceptionInfoCache(project, GlobalSearchScope.allScope(project))
    val entryPoint = findExpectedEntryPoint(stackTrace, exceptionCache) ?: return null
    val searchStacktrace = entryPoint.stackTrace
    var expectedParam: UParameter? = entryPoint.param
    val lineParser = ExceptionLineParserFactory.getInstance().create(exceptionCache)
    val expectedArgCandidates = mutableListOf<PsiElement>()
    searchStacktrace.lines().forEach { line ->
      lineParser.execute(line, line.length) ?: return@findExpected null
      val file = lineParser.file ?: return@findExpected null
      val diffProvider = TestDiffProvider.TEST_DIFF_PROVIDER_LANGUAGE_EXTENSION.forLanguage(file.language).asSafely<JvmTestDiffProvider>()
                         ?: return@findExpected null
      val failedCall = findFailedCall(file, lineParser.info.lineNumber, expectedParam?.getContainingUMethod()) ?: return@findExpected null
      expectedArgCandidates.addAll(failedCall.valueArguments.mapNotNull { diffProvider.getExpectedElement(it, expected) })
      if (expectedParam != null) { // precise tracking don't need to look through whole stack trace
        val containingMethod = expectedParam?.getContainingUMethod() ?: return@findExpected null

        val expectedArg = failedCall.getArgumentForParameter(containingMethod.uastParameters.indexOf(expectedParam))
                          ?: return@findExpected null
        diffProvider.getExpectedElement(expectedArg, expected)?.let { return it }
        if (expectedArg is UReferenceExpression) {
          val resolved = expectedArg.resolveToUElement()
          if (resolved is UVariable) {
            resolved.uastInitializer?.let { initializer -> diffProvider.getExpectedElement(initializer, expected)?.let { return it } }
          }
          expectedParam = if (resolved is UParameter && resolved.uastParent is UMethod) {
            val method = resolved.uastParent?.asSafely<UMethod>()
            if (method != null && !method.isConstructor) resolved else null
          }
          else null
        }
      }
    }
    if (expectedArgCandidates.size == 1) return expectedArgCandidates.first()
    return null
  }

  private data class ExpectedEntryPoint(val stackTrace: String, val param: UParameter)

  private fun findExpectedEntryPoint(stackTrace: String, exceptionCache: ExceptionInfoCache): ExpectedEntryPoint? {
    val lineParser = ExceptionLineParserFactory.getInstance().create(exceptionCache)
    stackTrace.lineSequence().forEach { line ->
      lineParser.execute(line, line.length) ?: return@forEach
      val file = lineParser.file ?: return@findExpectedEntryPoint null
      val failedCall = findFailedCall(file, lineParser.info.lineNumber, null) ?: return@forEach
      val entryParam = findExpectedEntryPointParam(failedCall) ?: return@forEach
      return ExpectedEntryPoint(line + stackTrace.substringAfter(line), entryParam)
    }
    return null
  }

  private fun findExpectedEntryPointParam(call: UCallExpression): UParameter? {
    val assertHint = UAssertHint.createAssertEqualsHint(call) ?: return null
    val srcCall = call.sourcePsi ?: return null
    val stringType = PsiType.getJavaLangString(srcCall.manager, srcCall.resolveScope)
    if (assertHint.expected.getExpressionType() != stringType || assertHint.actual.getExpressionType() != stringType) return null
    val method = call.resolveToUElement()?.asSafely<UMethod>() ?: return null
    if (method.name != "assertEquals") return null
    return method.uastParameters.firstOrNull()
  }

  private fun findFailedCall(file: PsiFile, lineNumber: Int, resolvedMethod: UMethod?): UCallExpression? {
    val virtualFile = file.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null
    if (lineNumber < 1 || lineNumber > document.lineCount) return null
    val startOffset = document.getLineStartOffset(lineNumber - 1)
    val endOffset = document.getLineEndOffset(lineNumber - 1)
    val candidateCalls = getCallElementsInRange(file, startOffset, endOffset) ?: return null
    return if (candidateCalls.size != 1) {
      candidateCalls.firstOrNull { call ->
        call.resolveToUElement().asSafely<UMethod>()?.sourcePsi?.isEquivalentTo(resolvedMethod?.sourcePsi) == true
      }
    }
    else candidateCalls.first()
  }

  private fun getCallElementsInRange(file: PsiFile, startOffset: Int, endOffset: Int): List<UCallExpression>? {
    val startElement = file.findElementAt(startOffset) ?: return null
    val searchStartOffset = startElement.startOffset
    val calls = mutableListOf<UCallExpression>()
    var curElement: PsiElement? = startElement
    while (curElement != null && curElement.startOffset in searchStartOffset..endOffset) {
      val callExpression = curElement.toUElement().getUCallExpression(searchLimit = 2)
      if (callExpression != null) calls.add(callExpression)
      curElement = curElement.nextSibling
    }
    return calls
  }

  protected fun String.withoutLineEndings() = replace("\n", "").replace("\r", "")
}