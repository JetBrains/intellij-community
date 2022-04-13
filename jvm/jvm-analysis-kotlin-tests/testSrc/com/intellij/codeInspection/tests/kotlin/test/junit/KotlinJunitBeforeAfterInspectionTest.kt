package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterInspectionTestBase

class KotlinJunitBeforeAfterInspectionTest : JUnitBeforeAfterInspectionTestBase() {
  fun testHighlighting() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Before
      
      class MainTest {
        @Before
        fun <warning descr="'before()' has incorrect signature for a '@org.junit.Before' method">before</warning>(i: Int): String { return "${'$'}i" }
      }
    """.trimIndent())
  }
}