// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.lang.java.JavaLanguage

class JavaFormatterBlankLinesAroundFieldTest : JavaFormatterTestCase() {
  override fun getBasePath(): String = "psi/formatter/blankLinesAroundField"

  override fun setUp() {
    super.setUp()
    customJavaSettings.BLANK_LINES_AROUND_FIELD_WITH_ANNOTATIONS = 2
    getSettings(JavaLanguage.INSTANCE).BLANK_LINES_AROUND_FIELD = 0
  }

  fun testAnnotatedFieldAfterNonAnnotatedFields() = doTest()

  fun testNoBlankLinesAfterLBraceAndBeforeRBrace() = doTest()

  fun testMultipleAnnotatedFields() = doTest()

  fun testTypeAnnotation() = doTest()

  fun testMixed() = doTest()

  fun testAnnotatedAndNonAnnotatedFieldsRespectEachOther() {
    getSettings(JavaLanguage.INSTANCE).BLANK_LINES_AROUND_FIELD = 1
    doTest()
  }

  fun testRearrangement() {
    getSettings(JavaLanguage.INSTANCE).BLANK_LINES_AROUND_FIELD = 1
    val testName = getTestName(true)
    val afterFileName = "${testName}_after"
    doTest(testName, afterFileName)
    doTest(afterFileName, afterFileName)
  }
}