// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class GeneratedAnnotationSuppressionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.addClass("package javax.annotation; public @interface Generated { String[] value(); }")
    myFixture.addClass("package javax.annotation.processing; public @interface Generated { String[] value(); }")
    myFixture.addClass("package jakarta.annotation; public @interface Generated { String[] value(); }")
    myFixture.enableInspections(UnusedDeclarationInspection(true))
  }

  @Test
  fun testNotAnnotated() {
    doTest("public class <warning descr=\"Class 'MyController' is never used\">MyController</warning> {}")
  }

  @Test
  fun testJavaxGenerated() {
    doTest("""
      @javax.annotation.Generated("Test")
      public class MyController {}
    """.trimIndent())
  }

  @Test
  fun testJavaxProcessingGenerated() {
    doTest("""
      @javax.annotation.processing.Generated("Test")
      public class MyController {}
    """.trimIndent())
  }

  @Test
  fun testJakartaGenerated() {
    doTest("""
      @jakarta.annotation.Generated("Test")
      public class MyController {}
    """.trimIndent())
  }

  private fun doTest(text: String) {
    myFixture.configureByText("MyController.java", text)
    myFixture.checkHighlighting()
  }
}
