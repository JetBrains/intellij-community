package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.TestMethodWithoutAssertionInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinTestMethodWithoutAssertionInspectionTest : TestMethodWithoutAssertionInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : JUnitProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test highlighting for empty method body`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
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
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      import org.junit.Test
      import org.junit.Assert
      import mockit.*

      class TestMethodWithoutAssertion : TestCase() {
          //@Test
          //public fun fourOhTest2() { Assert.assertTrue(true) }
          //
          //public fun test2() { assertTrue(true) }
          //
          //public fun test3() { fail() }

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
}