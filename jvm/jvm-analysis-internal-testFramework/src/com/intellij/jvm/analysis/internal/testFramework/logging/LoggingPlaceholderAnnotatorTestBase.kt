package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import junit.framework.TestCase

abstract class LoggingPlaceholderAnnotatorTestBase : LightJvmCodeInsightFixtureTestCase() {
  protected abstract val fileName: String

  override fun setUp() {
    super.setUp()
    LoggingTestUtils.addSlf4J(myFixture)
    LoggingTestUtils.addLog4J(myFixture)
    LoggingTestUtils.addJUL(myFixture)
    LoggingTestUtils.addKotlinAdapter(myFixture)
  }

  protected fun doTest(expectedText: String) {
    val expectedTextWithoutTags = expectedText.replace(Regex("</?$TAG>"), "")
    myFixture.configureByText(fileName, expectedTextWithoutTags)
    val highlightingList = myFixture.doHighlighting(HighlightSeverity.INFORMATION).filter {
      it.forcedTextAttributesKey?.externalName == "LOG_STRING_PLACEHOLDER"
    }

    val restoredText = CodeInsightTestFixtureImpl.getTagsFromSegments(myFixture.file.text, highlightingList, TAG, null)
    TestCase.assertEquals(expectedText, restoredText)
  }


  companion object {
    private const val TAG = "placeholder"
  }

}