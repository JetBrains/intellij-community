// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaRecordAnnotationsFormatterTest : JavaFormatterIdempotencyTestCase() {
  override fun getBasePath(): String = "psi/formatter/java/recordAnnotations"

  @BeforeEach
  fun setUp() {
    customJavaSettings.RECORD_COMPONENTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    customJavaSettings.ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT = true
  }

  @Test
  fun testNoAlignment() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = false
    doIdempotentTest()
  }

  @Test
  fun testNoAlignmentWithNewLineAfterLParen() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = false
    customJavaSettings.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    doIdempotentTest()
  }

  @Test
  fun testNoAlignmentWithNewLineBeforeRParen() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = false
    customJavaSettings.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    doIdempotentTest()
  }

  @Test
  fun testNoAlignmentWithBothParensOnNewLine() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = false
    customJavaSettings.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    customJavaSettings.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    doIdempotentTest()
  }


  @Test
  fun testAlignment() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testAlignmentWithNewLineAfterLParen() {
    customJavaSettings.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testAlignmentWithNewLineBeforeRParen() {
    customJavaSettings.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testAlignmentWithBothParensOnNewLine() {
    customJavaSettings.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    customJavaSettings.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testIgnoresFormattingKnownTypeAnnotationsWhenItIsLast() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testIgnoresFormattingKnownTypeAnnotationsWhenItIsFirst() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testIgnoresFormattingKnownTypeAnnotationsWhenItIsInMiddle() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testIgnoresFormattingKnownConsecutiveTypeAnnotations() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    doIdempotentTest()
  }

  @Test
  fun testBlankLinesBetweenRecordComponents() {
    customJavaSettings.ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT = false
    customJavaSettings.RECORD_COMPONENTS_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    customJavaSettings.BLANK_LINES_BETWEEN_RECORD_COMPONENTS = 3
    doIdempotentTest()
  }

  @Test
  fun testBlankLinesBetweenRecordComponentsWithAnnotations() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    customJavaSettings.BLANK_LINES_BETWEEN_RECORD_COMPONENTS = 2
    doIdempotentTest()
  }

  @Test
  fun testBlankLinesIgnoresWrapping() {
    customJavaSettings.RECORD_COMPONENTS_WRAP = CommonCodeStyleSettings.DO_NOT_WRAP
    customJavaSettings.ALIGN_MULTILINE_RECORDS = true
    customJavaSettings.BLANK_LINES_BETWEEN_RECORD_COMPONENTS = 4
    doIdempotentTest()
  }

  @Test
  fun testBlankLinesWithAnnotationsWithoutAlignment() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = false
    customJavaSettings.BLANK_LINES_BETWEEN_RECORD_COMPONENTS = 2
    doIdempotentTest()
  }

  @Test
  fun testBlankLinesWithAnnotationsRespectsNewLinesAfterParens() {
    customJavaSettings.ALIGN_MULTILINE_RECORDS = false
    customJavaSettings.BLANK_LINES_BETWEEN_RECORD_COMPONENTS = 2
    customJavaSettings.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER = true
    customJavaSettings.RPAREN_ON_NEW_LINE_IN_RECORD_HEADER = true
    doIdempotentTest()
  }
}