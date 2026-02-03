// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc


import com.intellij.codeInspection.javaDoc.ReplaceWithJavadocInspection
import com.intellij.java.JavaBundle
import com.siyeh.ig.IGQuickFixesTestCase

class ReplaceWithJavadocInspectionFixTest : IGQuickFixesTestCase() {

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ReplaceWithJavadocInspection())
    myRelativePath = "javadoc/replace_with_javadoc"
    myDefaultHint = JavaBundle.message("inspection.replace.with.javadoc")
  }

  fun testJavadocWithTags() {
    doTest()
  }

  fun testMergeJavadocWIthEndOfLineAndBlock() {
    doTest()
  }

  fun testEndOfLine() {
    doTest()
  }

  fun testEmptyEndOfLine() {
    doTest()
  }

  fun testEmptyBlockComment() {
    doTest()
  }

  fun testCommentsBeforeAndAfterModifierListTypeAndParams() {
    doTest()
  }

  fun testBlockComment() {
    doTest()
  }

  fun testJavadoc() {
    assertQuickfixNotAvailable()
  }
}
