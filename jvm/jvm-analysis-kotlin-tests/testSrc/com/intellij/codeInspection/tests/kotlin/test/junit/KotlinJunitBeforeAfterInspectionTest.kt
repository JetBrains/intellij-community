package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterInspectionTestBase

class KotlinJunitBeforeAfterInspectionTest : JUnitBeforeAfterInspectionTestBase() {
  fun testHighlightingBefore() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Before
      
      class MainTest {
        @Before
        fun <warning descr="'before()' has incorrect signature for a '@org.junit.Before' method">before</warning>(i: Int): String { return "${'$'}i" }
      }
    """.trimIndent())
  }

  fun testHighlightingBeforeEach() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.BeforeEach
      
      class MainTest {
        @BeforeEach
        fun <warning descr="'beforeEach()' has incorrect signature for a '@org.junit.jupiter.api.BeforeEach' method">beforeEach</warning>(i: Int): String { return "" }
      }
    """.trimIndent())
  }

  fun testQuickFixChangeRemoveModifier() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.BeforeEach
      
      class MainTest {
        @BeforeEach
        private fun bef<caret>oreEach() { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.BeforeEach
      
      class MainTest {
        @BeforeEach
        fun bef<caret>oreEach() { }
      }
    """.trimIndent(), "Remove 'private' modifier")
  }
}