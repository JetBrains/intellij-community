package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression

abstract class CallMatcherTestBase : LightJavaCodeInsightFixtureTestCase() {
  protected fun checkMatchCall(lang: JvmLanguage, call: CallMatcher, text: String) {
    val psiFile = myFixture.configureByText("UnderTest${lang.ext}", text)
    val expressions = JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile(psiFile, UCallExpression::class.java)
    assertTrue("$this doesn't contain $call", expressions.any { call.uCallMatches(it) })
  }

  protected fun checkMatchCallableReference(lang: JvmLanguage, call: CallMatcher, text: String) {
    val psiFile = myFixture.configureByText("UnderTest${lang.ext}", text)
    val expressions = JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile(psiFile, UCallableReferenceExpression::class.java)
    assertTrue("$this doesn't contain $call", expressions.any { call.uCallableReferenceMatches(it) })
  }
}