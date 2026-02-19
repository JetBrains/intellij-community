package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addAkka
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addJUL
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addKotlinAdapter
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addLog4J
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addSlf4J
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class LoggingInspectionTestBase : JvmInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    addSlf4J(myFixture)
    addLog4J(myFixture)
    addJUL(myFixture)
    addKotlinAdapter(myFixture)
    addAkka(myFixture)
  }
}