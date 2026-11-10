// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

@TestApplication
class IntroduceVariableIntentionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun `test create local variable from qualified call`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun `test create local variable from non filled argument`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun `test create local variable in if without braces`(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  private suspend fun doTestWithPreview(before: String, @Language("JAVA") after: String, intentionName: String) {
    myFixture.configureByText("X.java", before)
    checkPreviewAndResult(after, intentionName)
  }

  private suspend fun doTestCompletion(before: String, @Language("Java") after: String, intentionName: String) {
    myFixture.configureByText("X.java", before)
    myFixture.complete(CompletionType.BASIC)
    checkPreviewAndResult(after, intentionName)
  }

  private suspend fun checkPreviewAndResult(@Language("Java") after: String, intentionName: String) {
    withContext(Dispatchers.EDT) {
      myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(intentionName))
      myFixture.checkResult(after)
    }
  }
}