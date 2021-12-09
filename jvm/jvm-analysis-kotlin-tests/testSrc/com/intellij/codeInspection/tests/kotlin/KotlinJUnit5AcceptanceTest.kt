package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.codeInsight.JUnit5TestFrameworkSetupUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@RunsInEdt
class KotlinJUnit5AcceptanceTest : LightJavaCodeInsightFixtureTestCase5() {
  @BeforeEach
  internal fun setUp() {
    JUnit5TestFrameworkSetupUtil.setupJUnit5Library(fixture)
  }

  @Test
  fun compoundAnnotation() {
    fixture.addFileToProject("CombinedKotlinAnnotation.kt","""@org.junit.jupiter.api.Test
annotation class CombinedKotlinAnnotation""")
    val file = fixture.configureByText("tests.kt", """
      class Tests {
        @CombinedKotlinAnnotation
        fun testExampleKotlinAnnotation() {}
      }
    """.trimIndent())

    Assertions.assertNotNull(TestFrameworks.detectFramework ((file as PsiClassOwner).classes[0]))
  }

  @Test
  fun bracesInMethodName() {
    val file = fixture.configureByText("tests.kt", """
      class Tests {
         @org.junit.jupiter.api.Test
         fun `test wi<caret>th (in name)`() {}
      }
    """.trimIndent())
    Assertions.assertInstanceOf(PsiClassOwner::class.java, file)
    val testMethod = (file as PsiClassOwner).classes[0].methods[0]
    Assertions.assertEquals("test with (in name)()", JUnitConfiguration.Data.getMethodPresentation(testMethod))
  }
}