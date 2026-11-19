package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.testCaseWithConstructorInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.test.testCaseWithConstructorProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.PathUtil
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
abstract class KotlinTestCaseWithConstructorInspectionTest {
  companion object {
    private val projectFixture = testCaseWithConstructorProjectFixture {
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(this, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  protected val myFixture by testCaseWithConstructorInspectionFixture(projectFixture)

  @Test
  fun `test no highlighting parameterized test case`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.Test
      import org.junit.runner.RunWith
      import org.junit.runners.Parameterized
      import org.junit.runners.Parameterized.Parameters

      @RunWith(Parameterized::class)
      class ParameterizedTest(private val x: Int, private val y: Int) {
        @Test
        public fun testMe() { }

        companion object {
          @JvmStatic
          @Parameterized.Parameters
          public fun parameters(): Array<Array<Any>> = arrayOf(arrayOf(1, 2), arrayOf(3, 4))
        }
      }
    """.trimIndent(), "ParameterizedTest")
  }

  @Test
  fun `test no highlighting trivial constructor`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase
      
      class TestCaseWithConstructorInspection2() : TestCase() {
          constructor(x: Int) : this() {
              if (false) {
                  println(x)
              }
          }
      }
    """.trimIndent(), "TestCaseWithConstructorInspection2")
  }

  @Test
  fun `test highlighting simple non-trivial constructor`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase

      class TestCaseWithConstructorInspection1() : TestCase() {
          <warning descr="Initialization logic in constructor 'constructor()' instead of 'setup()' life cycle method">constructor</warning>(x: Int) : this() {
              println(x)
          }
      }
    """.trimIndent(), "TestCaseWithConstructorInspection1")
  }

  @Test
  fun `test highlighting Junit 4`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit4TestCaseWithConstructor {
        <warning descr="Initialization logic in constructor 'constructor()' instead of 'setup()' life cycle method">constructor</warning>() {
          println()
          println()
          println()
        }

        @org.junit.Test
        public fun testMe() {}
      }
    """.trimIndent(), "JUnit4TestCaseWithConstructor")
  }
}