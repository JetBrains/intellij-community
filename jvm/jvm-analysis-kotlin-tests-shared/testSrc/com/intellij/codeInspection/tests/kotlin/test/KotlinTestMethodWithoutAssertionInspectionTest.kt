package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestMethodWithoutAssertionInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinTestMethodWithoutAssertionInspectionTest : TestMethodWithoutAssertionInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }

  fun `test highlighting for empty method body`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase
      import org.junit.Test
      import org.junit.Assert

      class TestMethodWithoutAssertion : TestCase() {
          public fun <warning descr="Test method 'test()' contains no assertions">test</warning>() { }

          @Test
          public fun <warning descr="Test method 'fourOhTest()' contains no assertions">fourOhTest</warning>() { }

          @Test(expected = Exception::class)
          public fun fourOhTestWithExpected() { }
      }
    """.trimIndent())
  }

  fun `test no highlighting when assertion is present`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase
      import org.junit.Test
      import org.junit.Assert
      import mockit.*

      class TestMethodWithoutAssertion : TestCase() {
          @Test
          public fun fourOhTest2() { Assert.assertTrue(true) }

          public fun test2() { assertTrue(true) }

          public fun test3() { fail() }

          @Test 
          public fun delegateOnly() { check() }

          @Test
          public fun delegateAdditionally() {
              val i = 9
              println(i)
              check()
          }

          private fun check() { Assert.assertTrue(true) }

          @Test
          public fun testExecuteReverseAcknowledgement(@Mocked messageDAO: Any)  {
              println(messageDAO)
              
              object : Verifications() { }
          }

          @Test
          @Throws(AssertionError::class)
          public fun testMethodWhichThrowsExceptionOnFailure() {
              if (true) throw AssertionError()
          }
      }
    """.trimIndent())
  }

  fun `test no highlighting kotlin stdlib assertion`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.Test
      import kotlin.test.*
      
      class TestMethodWithAssertion {
        @Test
        public fun ktPrecondition1() { assert(true) }

        @Test
        public fun ktTestAssertion1() { assertTrue(true) }
      }
    """.trimIndent())
  }

  fun `test no highlighting kotlin JUnit 5 assertion`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.assertDoesNotThrow
      
      class TestMethodWithAssertion {
        @Test
        fun testFoo() {
            assertDoesNotThrow<IllegalStateException> { throw IllegalStateException() }
        }
      }
    """.trimIndent())
  }

  fun `test no highlighting mockk assertion`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.api.Test
      import io.mockk.verify
      
      class TestMethodWithAssertion {
        @Test
        fun testFoo() {
            verify { }
        }
      }
    """.trimIndent())
  }
}