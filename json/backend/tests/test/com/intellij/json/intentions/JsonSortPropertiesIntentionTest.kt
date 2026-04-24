// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.intentions

import com.intellij.json.JsonBundle
import com.intellij.json.JsonTestCase
import com.intellij.json.syntax.JsonLazyParsing
import org.junit.AssumptionViolatedException

class JsonSortPropertiesIntentionTest : JsonTestCase() {
  private fun doTest() {
    myFixture.testHighlighting("/intention/" + getTestName(false) + ".json")
    val intention = myFixture.getAvailableIntention(JsonBundle.message("json.intention.sort.properties"))
    requireNotNull(intention)
    myFixture.launchAction(intention)
    myFixture.checkResultByFile("/intention/" + getTestName(false) + "_after.json")
  }

  fun testSortProperties() {
    doTest()
  }

  fun testSortMalformedJson() {
    if (!JsonLazyParsing) throw AssumptionViolatedException("lazy pasting is off")
    doTest()
  }

  fun testSortMalformedJson_non_lazy() {
    if (JsonLazyParsing) throw AssumptionViolatedException("Lazy pasting is on")
    doTest()
  }

  fun testSortPropertiesShouldReformat() {
    doTest()
  }

  fun testSortRecursively() {
    doTest()
  }

  fun testSortWhenCaretAfterLastSymbol() {
    doTest()
  }
}