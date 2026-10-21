// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.intentions

import com.intellij.json.JsonBundle
import com.intellij.json.syntax.JsonLazyParsing
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testNameFixture
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

@TestApplication
@TestDataPath($$"$PROJECT_ROOT/community/json/backend/tests/testData")
class JsonSortPropertiesIntentionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)
  private val testName by testNameFixture(false)

  private fun doTest() {
    myFixture.testHighlighting("/intention/$testName.json")
    val intention = myFixture.getAvailableIntention(JsonBundle.message("json.intention.sort.properties"))
    requireNotNull(intention)
    myFixture.launchAction(intention)
    myFixture.checkResultByFile("/intention/${testName}_after.json")
  }

  @Test
  fun testSortProperties() {
    doTest()
  }

  @Test
  fun testSortMalformedJson() {
    assumeTrue(JsonLazyParsing, "lazy pasting is off")
    doTest()
  }

  @Test
  fun testSortMalformedJson_non_lazy() {
    assumeTrue(!JsonLazyParsing, "Lazy pasting is on")
    doTest()
  }

  @Test
  fun testSortPropertiesShouldReformat() {
    doTest()
  }

  @Test
  fun testSortRecursively() {
    doTest()
  }

  @Test
  fun testSortWhenCaretAfterLastSymbol() {
    doTest()
  }
}