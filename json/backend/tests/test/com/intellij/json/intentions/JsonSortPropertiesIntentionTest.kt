// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.intentions

import com.intellij.json.JsonTestCase

class JsonSortPropertiesIntentionTest : JsonTestCase() {
  private fun doTest() {
    myFixture.testHighlighting("/intention/" + getTestName(false) + ".json")
    myFixture.launchAction(myFixture.getAvailableIntention(JsonSortPropertiesIntention().text)!!)
    myFixture.checkResultByFile("/intention/" + getTestName(false) + "_after.json")
  }

  fun testSortProperties() {
    doTest()
  }

  fun testSortMalformedJson() {
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