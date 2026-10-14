// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaFormatterBlankLinesAroundFieldTest : JavaFormatterTestCase() {
  override fun getBasePath(): String = "psi/formatter/blankLinesAroundField"

  @BeforeEach
  fun setUp() {
    customJavaSettings.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS = 2
    getSettings(JavaLanguage.INSTANCE).BLANK_LINES_AROUND_FIELD = 0
  }

  @Test
  fun testAnnotatedFieldAfterNonAnnotatedFields() = doTest()

  @Test
  fun testNoBlankLinesAfterLBraceAndBeforeRBrace() = doTest()

  @Test
  fun testMultipleAnnotatedFields() = doTest()

  @Test
  fun testTypeAnnotation() = doTest()

  @Test
  fun testMixed() = doTest()

  @Test
  fun testAnnotatedAndNonAnnotatedFieldsRespectEachOther() {
    getSettings(JavaLanguage.INSTANCE).BLANK_LINES_AROUND_FIELD = 1
    doTest()
  }

  @Test
  fun testRearrangement() {
    getSettings(JavaLanguage.INSTANCE).BLANK_LINES_AROUND_FIELD = 1
    val testName = getTestName(true)
    val afterFileName = "${testName}_after"
    doTest(testName, afterFileName)
    doTest(afterFileName, afterFileName)
  }
}