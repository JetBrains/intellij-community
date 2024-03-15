package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase

abstract class LoggingArgumentSymbolReferenceProviderTestBase : LightJvmCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    LoggingTestUtils.addSlf4J(myFixture)
    LoggingTestUtils.addLog4J(myFixture)
    LoggingTestUtils.addJUL(myFixture)
    LoggingTestUtils.addKotlinAdapter(myFixture)
  }
}