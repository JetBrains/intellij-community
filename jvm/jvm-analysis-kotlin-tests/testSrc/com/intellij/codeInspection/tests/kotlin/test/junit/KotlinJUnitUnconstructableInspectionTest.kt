package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitUnconstructableTestCaseInspectionTestBase

class KotlinJUnitUnconstructableInspectionTest : JUnitUnconstructableTestCaseInspectionTestBase() {
  fun testPlain() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Plain { }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase1() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase

      class <warning descr="Test class 'UnconstructableJUnit3TestCase1' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase1</warning> private constructor() : TestCase() {
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase2() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase

      class <warning descr="Test class 'UnconstructableJUnit3TestCase2' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase2</warning>(val foo: Any) : TestCase() {
        fun bar() { }
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase3() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase

      class UnconstructableJUnit3TestCase3() : TestCase() { }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase4() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase

      class UnconstructableJUnit3TestCase4(val foo: String) : TestCase() { }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCaseAnynoymousObject() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      
      class Test {
          private val testCase = object : TestCase() { }
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCaseLocalClass() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      
      fun main () {
        class LocalClass : TestCase() { }
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit4TestCase1() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Test
      
      class <warning descr="Test class 'UnconstructableJUnit4TestCase1' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase1</warning>() {
        constructor(args: String) : this() { args.plus("") }
      
        @Test
        fun testMe() {}
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit4TestCase2() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Test

      class UnconstructableJUnit4TestCase2() {
        private constructor(one: String, two: String) : this() { one.plus(two) }

      	@Test
      	public fun testAssertion() { }
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit4TestCase3() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Test

      private class <warning descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it is not 'public'">UnconstructableJUnit4TestCase3</warning>() {

        @Test
        fun testMe() {}
      }
    """.trimIndent())
  }
}