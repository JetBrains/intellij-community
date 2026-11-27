// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.javadoc


import com.intellij.codeInspection.javaDoc.ReplaceWithJavadocInspection
import com.intellij.java.JavaBundle
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.igQuickFixesFixture
import org.junit.jupiter.api.Test

@TestApplication
class ReplaceWithJavadocInspectionFixTest {

  private val ig by igQuickFixesFixture({ listOf(ReplaceWithJavadocInspection()) }, "javadoc/replace_with_javadoc",
                                        JavaBundle.message("inspection.replace.with.javadoc"))

  @Test
  fun testJavadocWithTags() {
    ig.doTest()
  }

  @Test
  fun testMergeJavadocWIthEndOfLineAndBlock() {
    ig.doTest()
  }

  @Test
  fun testEndOfLine() {
    ig.doTest()
  }

  @Test
  fun testEmptyEndOfLine() {
    ig.doTest()
  }

  @Test
  fun testEmptyBlockComment() {
    ig.doTest()
  }

  @Test
  fun testCommentsBeforeAndAfterModifierListTypeAndParams() {
    ig.doTest()
  }

  @Test
  fun testBlockComment() {
    ig.doTest()
  }

  @Test
  fun testJavadoc() {
    ig.assertQuickfixNotAvailable()
  }
}
