// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.annotations.TestOnly
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The fixture of a [CallMatcher] test. Create it with [callMatcherFixture].
 */
@TestOnly
class CallMatcherFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Configures [text] as a file of [lang] and checks that [call] matches one of its call expressions.
   */
  fun checkMatchCall(lang: JvmLanguage, call: CallMatcher, text: String) {
    val psiFile = fixture.configureByText("UnderTest${lang.ext}", text)
    runReadActionBlocking {
      val expressions = JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile(psiFile, UCallExpression::class.java)
      assertTrue(expressions.any { call.uCallMatches(it) }, "$this doesn't contain $call")
    }
  }

  /**
   * Configures [text] as a file of [lang] and checks that [call] matches one of its callable references.
   */
  fun checkMatchCallableReference(lang: JvmLanguage, call: CallMatcher, text: String) {
    val psiFile = fixture.configureByText("UnderTest${lang.ext}", text)
    runReadActionBlocking {
      val expressions = JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile(psiFile, UCallableReferenceExpression::class.java)
      assertTrue(expressions.any { call.uCallableReferenceMatches(it) }, "$this doesn't contain $call")
    }
  }
}

/**
 * Creates a [CallMatcherFixture] for one test. It replaces [CallMatcherTestBase]. Declare it as an instance field.
 */
@TestOnly
fun callMatcherFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<CallMatcherFixture> = testFixture("callMatcherFixture") {
  initialized(CallMatcherFixture(lightJavaCodeInsightFixture(projectFixture).init())) {}
}
