package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterClassInspectionTestBase

class KotlinJunitBeforeAfterClassInspectionTest : JUnitBeforeAfterClassInspectionTestBase() {
  fun testHighlighting() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.BeforeAll
      
      class MainTest {
        @BeforeAll
        fun <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(i: Int): String { return "${'$'}i" }
      }
    """.trimIndent())
  }
}