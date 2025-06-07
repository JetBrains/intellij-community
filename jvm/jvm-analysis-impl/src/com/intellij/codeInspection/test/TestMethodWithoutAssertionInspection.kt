// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.TestFrameworks
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.asSafely
import com.siyeh.InspectionGadgetsBundle
import com.siyeh.ig.junit.JUnitCommonClassNames.*
import com.siyeh.ig.psiutils.MethodMatcher
import com.siyeh.ig.psiutils.TestUtils
import org.jdom.Element
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

class TestMethodWithoutAssertionInspection : AbstractBaseUastLocalInspectionTool() {
  @JvmField
  var assertKeywordIsAssertion = false

  @JvmField
  var ignoreIfExceptionThrown = false

  private val methodMatcher = MethodMatcher(false, "assertionMethods")
    .add(ORG_JUNIT_ASSERT, "assert.*|fail.*")
    .add(JUNIT_FRAMEWORK_ASSERT, "assert.*|fail.*")
    .add(ORG_JUNIT_JUPITER_API_ASSERTIONS, "assert.*|fail.*")
    .add(ORG_JUNIT_JUPITER_API_ASSERTIONS_KT, "assert.*|fail.*")
    .add("org.assertj.core.api.Assertions", "assertThat.*")
    .add("org.assertj.core.api.WithAssertions", "assertThat")
    .add("com.google.common.truth.Truth", "assert.*")
    .add("com.google.common.truth.Truth8", "assert.*")
    .add("org.mockito.Mockito", "verify.*")
    .add("org.mockito.InOrder", "verify")
    .add("org.junit.rules.ExpectedException", "expect.*")
    .add("io.mockk.MockKKt", "verify.*")
    .add("org.hamcrest.MatcherAssert", "assertThat")
    .add("mockit.Verifications", "Verifications")
    .add("kotlin.PreconditionsKt__AssertionsJVMKt", "assert")
    .add("kotlin.test.AssertionsKt__AssertionsKt", "assert.*|fail.*|expect") // for K1
    .add("kotlin.test.AssertionsKt", "assert.*|fail.*|expect") // for K2
    .add("org.testng.Assert", "assert.*|fail.*|expect.*")
    .add("org.testng.AssertJUnit", "assert.*|fail.*")
    .finishDefault()

  override fun getOptionsPane(): OptPane = pane(
    methodMatcher.getTable(InspectionGadgetsBundle.message("inspection.test.method.without.assertion.list.name")).prefix("methodMatcher"),
    checkbox("assertKeywordIsAssertion", InspectionGadgetsBundle.message("assert.keyword.is.considered.an.assertion")),
    checkbox("ignoreIfExceptionThrown", InspectionGadgetsBundle.message("inspection.test.method.without.assertions.exceptions.option")),
  )

  override fun readSettings(node: Element) {
    methodMatcher.readSettings(node)
    super.readSettings(node)
  }

  override fun writeSettings(node: Element) {
    methodMatcher.writeSettings(node)
    super.writeSettings(node)
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor =
    UastHintedVisitorAdapter.create(
      holder.file.language,
      TestMethodWithoutAssertionVisitor(holder, methodMatcher, assertKeywordIsAssertion, ignoreIfExceptionThrown),
      arrayOf(UMethod::class.java),
      directOnly = true
    )
}

private class TestMethodWithoutAssertionVisitor(
  private val holder: ProblemsHolder,
  private val methodMatcher: MethodMatcher,
  private val assertKeywordIsAssertion: Boolean,
  private val ignoreIfExceptionThrown: Boolean
) : AbstractUastNonRecursiveVisitor() {
  override fun visitMethod(node: UMethod): Boolean {
    val javaMethod = node.javaPsi
    if (!TestFrameworks.getInstance().isTestMethod(javaMethod)) return true
    if (TestUtils.hasExpectedExceptionAnnotation(javaMethod)) return true
    if (ignoreIfExceptionThrown && javaMethod.throwsList.referenceElements.isNotEmpty()) return true
    if (containsAssertion(node)) return true
    if (lastStatementIsCallToMethodWithAssertion(node)) return true
    val message = JvmAnalysisBundle.message("jvm.inspections.test.method.without.assertion.problem.descriptor")
    holder.registerUProblem(node, message)
    return true
  }

  private fun lastStatementIsCallToMethodWithAssertion(method: UMethod): Boolean {
    val lastExpression = when (method.uastBody) {
                           is UBlockExpression -> method.uastBody.asSafely<UBlockExpression>()?.expressions?.lastOrNull()
                           else -> method.uastBody
                         } ?: return false
    val callExpression = lastExpression.asSafely<UCallExpression>() ?: return false
    val targetMethod = callExpression.resolveToUElementOfType<UMethod>() ?: return false
    return containsAssertion(targetMethod)
  }

  private fun containsAssertion(element: UMethod): Boolean {
    val visitor = ContainsAssertionVisitor()
    element.uastBody?.accept(visitor)
    return visitor.containsAssertion
  }

  private inner class ContainsAssertionVisitor : AbstractUastVisitor() {
    var containsAssertion = false

    override fun visitElement(node: UElement): Boolean {
      if (containsAssertion) return true
      return node.sourcePsi is PsiCompiledElement // we don't expect assertions in libraries
    }

    override fun visitObjectLiteralExpression(node: UObjectLiteralExpression): Boolean = visitCallExpression(node)

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (containsAssertion) return true
      if ((assertKeywordIsAssertion && node.methodIdentifier == null && node.methodName == "assert")
          || methodMatcher.matches(node.resolve())) {
        containsAssertion = true
        return true
      }
      return false
    }
  }
}