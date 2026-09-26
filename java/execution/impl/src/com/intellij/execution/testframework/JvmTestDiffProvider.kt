// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework

import com.intellij.execution.filters.ExceptionInfoCache
import com.intellij.execution.filters.ExceptionLineParserFactory
import com.intellij.execution.testframework.actions.TestDiffProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiLiteralUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.asSafely
import com.siyeh.ig.testFrameworks.UAssertHint
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.expressions.UInjectionHost
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getUCallExpression
import org.jetbrains.uast.resolveToUElement
import org.jetbrains.uast.resolveToUElementOfType
import org.jetbrains.uast.toUElement

open class JvmTestDiffProvider : TestDiffProvider {
  override fun updateExpected(element: PsiElement, actual: String) {
    val content = prepareContent(element, actual) ?: return
    ElementManipulators.getManipulator(element)?.handleContentChange(element, content)
  }

  /**
   * Returns the content to write into the literal [element] so that the test compares [actual], or null when no content does.
   */
  protected open fun prepareContent(element: PsiElement, actual: String): String? =
    if (element !is PsiLiteralExpression || !element.isTextBlock) actual
    else PsiLiteralUtil.escapeBackSlashesInTextBlock(actual)

  /**
   * Returns the expression that carries the expected value when [expression] is a call that changes this value, and [expression] otherwise.
   *
   * A language that overrides this method must apply the same change in [comparedValue].
   */
  protected open fun unwrapExpected(expression: PsiElement): PsiElement = expression

  /**
   * Returns the value that the test compares when the content of [literal] is [value].
   *
   * Returns null when the test changes the value in a way that the provider cannot reproduce.
   */
  protected open fun comparedValue(literal: PsiElement, value: String): String? = value

  /**
   * Returns true when the frame of [methodName] is a bridge that the compiler generated between a call and its callee.
   *
   * The frame of such a bridge holds no call, so the stack-trace walk steps over it.
   */
  protected open fun isSyntheticBridge(methodName: String?): Boolean = false

  /**
   * Finds the expected value from a [stackTrace]. To do this the following algorithm is used:
   *
   * 1. We start traversing the stack trace to find the entry point of our search which is the assert equals call.
   * 2. We look at the expected arguments of the entry point, if it's a string literal or similar (like a Kotlin trim indented string
   * literal), we found our expected element, if not we resolve the reference and check for 3 options:
   *    1. The reference resolved to a field declaration. Here we look at the initializer and check whether it is the expected value.
   *    2. The reference resolved to a parameter of the method. We go further down the stack trace and repeat step 2 with the next call
   *    as the entry point. A frame of a compiler-generated bridge holds no call, so we step over it (see [isSyntheticBridge]).
   *    3. The reference resolved to a local variable that a parameter of the method initializes, such as `val text = param`, or
   *    `val text = param.trimIndent()` in Kotlin (see [unwrapExpected]). The local is in the frame of the current call, so it does not
   *    move the walk along the stack trace. We track `param` instead of the local and continue as in option 2.
   *    It might happen that we can't find the specific call in sources, for example when the call comes from a library. We now lost the
   *    parameter that we were tracking, so we try another (less accurate and more expensive) strategy as described in step 3.
   * 3. We find all calls that we haven't checked in previous steps, collect all arguments that look like string literals and compare its
   * content with the [expected] value as reported from our test output. If exactly 1 string literal content matches this expected value
   * we return this element. If we found 0 or more than 1 matching elements we return null.
   */
  override fun findExpected(project: Project, stackTrace: String, expected: String): PsiElement? {
    val exceptionCache = ExceptionInfoCache(project, GlobalSearchScope.allScope(project))
    val entryPoint = findExpectedEntryPoint(stackTrace, exceptionCache) ?: return null
    val searchStacktrace = entryPoint.stackTrace
    var expectedParam: UParameter? = entryPoint.param
    val lineParser = ExceptionLineParserFactory.getInstance().create(exceptionCache)
    val expectedArgCandidates = mutableListOf<PsiElement>()
    // The walk stops at the first frame without project source, such as a frame of the test runner.
    // The frames above it hold the expected literal.
    for (line in searchStacktrace.lines()) {
      ProgressManager.checkCanceled()
      lineParser.execute(line, line.length) ?: break
      val file = lineParser.file ?: break
      val diffProvider = TestDiffProvider.getProviderByLanguage(file.language).asSafely<JvmTestDiffProvider>() ?: break
      if (diffProvider.isSyntheticBridge(lineParser.method)) continue
      val failedCall = findFailedCall(file, lineParser.info.lineNumber, expectedParam?.getContainingUMethod()) ?: break
      if (failedCall.sourcePsi?.isValid != true) continue
      expectedArgCandidates.addAll(failedCall.valueArguments.mapNotNull { diffProvider.getExpectedElement(it, expected) })
      if (expectedParam != null) { // precise tracking don't need to look through whole stack trace
        val containingMethod = expectedParam.getContainingUMethod() ?: return null

        val expectedArg = failedCall.getArgumentForParameter(containingMethod.uastParameters.indexOf(expectedParam))
                          ?: return null
        diffProvider.getExpectedElement(expectedArg, expected)?.let { return it }
        if (expectedArg is UReferenceExpression) {
          if (expectedArg.sourcePsi?.isValid == true) {
            val resolved = expectedArg.resolveToUElement()
            if (resolved is UVariable) {
              resolved.uastInitializer?.let { initializer -> diffProvider.getExpectedElement(initializer, expected)?.let { return it } }
            }
            expectedParam = diffProvider.trackedParameter(resolved)
          }
          else {
            expectedParam = null
          }
        }
      }
    }
    if (expectedArgCandidates.size == 1) return expectedArgCandidates.first()
    return null
  }

  /**
   * Returns the method parameter that carries the expected value on from the resolved reference [resolved].
   *
   * A local such as `val text = fileContent` carries `fileContent` on.
   * The initializer can be a call that [unwrapExpected] removes, because the match compares the value that [comparedValue] returns.
   */
  private fun trackedParameter(resolved: UElement?): UParameter? {
    var element = resolved
    val visited = HashSet<PsiElement>()
    while (element is ULocalVariable) {
      if (!visited.add(element.sourcePsi ?: return null)) return null
      val initializer = element.uastInitializer ?: return null
      element = unwrapExpected(initializer).asSafely<UReferenceExpression>()?.resolveToUElement()
    }
    if (element !is UParameter) return null
    val method = element.uastParent.asSafely<UMethod>() ?: return null
    return if (method.isConstructor) null else element
  }

  private data class ExpectedEntryPoint(val stackTrace: String, val param: UParameter)

  private fun findExpectedEntryPoint(stackTrace: String, exceptionCache: ExceptionInfoCache): ExpectedEntryPoint? {
    val lineParser = ExceptionLineParserFactory.getInstance().create(exceptionCache)
    stackTrace.lineSequence().forEach { line ->
      ProgressManager.checkCanceled()
      lineParser.execute(line, line.length) ?: return@forEach
      val file = lineParser.file ?: return@findExpectedEntryPoint null
      val failedCall = findFailedCall(file, lineParser.info.lineNumber, null) ?: return@forEach
      val entryParam = findExpectedEntryPointParam(failedCall) ?: return@forEach
      return ExpectedEntryPoint(line + stackTrace.substringAfter(line), entryParam)
    }
    return null
  }

  private fun findExpectedEntryPointParam(call: UCallExpression): UParameter? {
    val srcCall = call.sourcePsi ?: return null
    if (!srcCall.isValid) return null
    val assertHint = UAssertHint.createAssertEqualsHint(call) ?: return null
    val stringType = PsiType.getJavaLangString(srcCall.manager, srcCall.resolveScope)
    if (assertHint.expected.getExpressionType() != stringType || assertHint.actual.getExpressionType() != stringType) return null
    val method = call.resolveToUElementOfType<UMethod>() ?: return null
    if (method.name != "assertEquals") return null
    return method.uastParameters.firstOrNull()
  }

  private fun findFailedCall(file: PsiFile, lineNumber: Int, resolvedMethod: UMethod?): UCallExpression? {
    if (file is PsiCompiledElement) return null
    val virtualFile = file.virtualFile ?: return null
    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return null
    if (lineNumber < 1 || lineNumber > document.lineCount) return null
    val startOffset = document.getLineStartOffset(lineNumber - 1)
    val endOffset = document.getLineEndOffset(lineNumber - 1)
    val candidateCalls = getCallElementsInRange(file, startOffset, endOffset) ?: return null
    if (candidateCalls.isEmpty()) return getNestedCallElementInRange(file, startOffset, endOffset)
    return if (candidateCalls.size != 1) {
      candidateCalls.firstOrNull { call ->
        call.resolveToUElementOfType<UMethod>()?.sourcePsi?.isEquivalentTo(resolvedMethod?.sourcePsi) == true
      }
    }
    else candidateCalls.first()
  }

  /**
   * Returns the only call whose name is on the line, or null when there is not exactly one.
   *
   * This scans the tokens of the line, so it finds a call in an expression body such as `fun testFoo() = doTest("expected")`.
   * A call that closes a multi-line literal from this line has its name on a different line, so it does not count.
   */
  private fun getNestedCallElementInRange(file: PsiFile, startOffset: Int, endOffset: Int): UCallExpression? {
    val calls = LinkedHashMap<PsiElement, UCallExpression>()
    var leaf = file.findElementAt(startOffset)
    while (leaf != null && leaf.startOffset <= endOffset) {
      val call = leaf.toUElement().getUCallExpression(searchLimit = 3)
      val name = call?.methodIdentifier?.sourcePsi ?: call?.sourcePsi
      if (call != null && name != null && name.startOffset in startOffset..endOffset) {
        calls.putIfAbsent(name, call)
      }
      leaf = PsiTreeUtil.nextLeaf(leaf)
    }
    return calls.values.singleOrNull()
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

  private fun getExpectedElement(expression: UExpression, expected: String): PsiElement? {
    val (host, value) = expression.asExpectedLiteral() ?: return null
    if (value == expected || value.withoutLineEndings() == expected.withoutLineEndings()) {
      return host.sourcePsi
    }
    return null
  }

  /**
   * Returns the string literal of this expression and the value that the test compares.
   */
  private fun UExpression.asExpectedLiteral(): Pair<UInjectionHost, String>? {
    val host = unwrapExpected(this).asSafely<UInjectionHost>() ?: return null
    val literal = host.sourcePsi ?: return null
    return host to (comparedValue(literal, host.evaluateString() ?: return null) ?: return null)
  }

  private fun unwrapExpected(expression: UExpression): UElement? {
    val source = expression.sourcePsi ?: return expression
    val unwrapped = unwrapExpected(source)
    return if (unwrapped == source) expression else unwrapped.toUElement()
  }

  private fun String.withoutLineEndings() = replace("\n", "").replace("\r", "")

  override fun getExpectedValue(element: PsiElement): String {
    if (element is PsiLiteralExpression) {
      val value = element.value
      if (value is String) return value
    }
    return ElementManipulators.getValueText(element)
  }
}
