// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnit5ConverterInspectionTestBase

class KotlinJUnit5ConverterInspectionTest9 : JUnit5ConverterInspectionTestBase() {
  fun `test qualified conversion`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      import org.junit.Test
      import org.junit.Assert

      class Qual<caret>ified {
          @Test
          fun testMethodCall() {
              Assert.assertArrayEquals(arrayOf<Any>(), null)
              Assert.assertArrayEquals("message", arrayOf<Any>(), null)
              Assert.assertEquals("Expected", "actual")
              Assert.assertEquals("message", "Expected", "actual")
              Assert.fail()
              Assert.fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(Assert::assertTrue)
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions

      class Qualified {
          @Test
          fun testMethodCall() {
              Assertions.assertArrayEquals(arrayOf<Any>(), null)
              Assertions.assertArrayEquals(arrayOf<Any>(), null, "message")
              Assertions.assertEquals("Expected", "actual")
              Assertions.assertEquals("Expected", "actual", "message")
              Assertions.fail()
              Assertions.fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(Assertions::assertTrue)
          }
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  fun `test unqualified conversion`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      import org.junit.Test
      import org.junit.Assert.*

      class UnQual<caret>ified {
          @Test
          fun testMethodCall() {
              assertArrayEquals(arrayOf<Any>(), null)
              assertArrayEquals("message", arrayOf<Any>(), null)
              assertEquals("Expected", "actual")
              assertEquals("message", "Expected", "actual")
              fail()
              fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(::assertTrue)
          }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions

      class UnQualified {
          @Test
          fun testMethodCall() {
              Assertions.assertArrayEquals(arrayOf<Any>(), null)
              Assertions.assertArrayEquals(arrayOf<Any>(), null, "message")
              Assertions.assertEquals("Expected", "actual")
              Assertions.assertEquals("Expected", "actual", "message")
              Assertions.fail()
              Assertions.fail("")
          }

          @Test
          fun testMethodRef() {
              fun foo(param: (Boolean) -> Unit) = param(false)
              foo(Assertions::assertTrue)
          }
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  fun `test expected on test annotation`() {
    myFixture.testQuickFixUnavailable(JvmLanguage.KOTLIN, """
      import org.junit.Assert.*
      import org.junit.jupiter.api.Test

      class ExpectedOn<caret>TestAnnotation {
          @Test
          fun testFirst() { }
      }
    """.trimIndent())
  }
}