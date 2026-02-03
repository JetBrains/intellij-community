// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class IntroduceVariableIntentionTest : LightJavaCodeInsightFixtureTestCase() {
  fun `test create local variable from qualified call`() {
    doTestWithPreview("""
      class X {
          public static void foo(int i) {
              String.<caret>valueOf(i);
          }
      }
    """.trimIndent(), """
      class X {
          public static void foo(int i) {
              String s = String.valueOf(i);
          }
      }
    """.trimIndent(), "Introduce local variable")
  }

  fun `test create local variable from non filled argument`() {
    doTestCompletion("""
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              ba<caret>
          }
      }
    """.trimIndent(), """
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              int i = ;
              bar(i);
          }
      }
    """.trimIndent(), "Introduce local variable")
  }

  fun `test create local variable in if without braces`() {
    doTestCompletion("""
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              if (true) ba<caret>
          }
      }
    """.trimIndent(), """
      class X {
          public static void bar(int i) { }
      
          public static void foo(String foo) {
              if (true) {
                  int i = ;
                  bar(i);
              }
          }
      }
    """.trimIndent(), "Introduce local variable")
  }

  private fun doTestWithPreview(before: String, @Language("JAVA") after: String, intentionName: String) {
    myFixture.configureByText("X.java", before)
    checkPreviewAndResult(after, intentionName)
  }

  private fun doTestCompletion(before: String, @Language("Java") after: String, intentionName: String) {
    myFixture.configureByText("X.java", before)
    myFixture.complete(CompletionType.BASIC)
    checkPreviewAndResult(after, intentionName)
  }

  private fun checkPreviewAndResult(@Language("Java") after: String, intentionName: String) {
    myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(intentionName))
    myFixture.checkResult(after)
  }
}