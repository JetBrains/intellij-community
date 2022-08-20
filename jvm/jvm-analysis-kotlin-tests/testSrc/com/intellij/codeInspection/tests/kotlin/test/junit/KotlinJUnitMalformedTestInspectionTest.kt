package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMalformedTestInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class  KotlinJUnitMalformedTestInspectionTest : JUnitMalformedTestInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun testJUnit3TestMethodIsPublicVoidNoArg() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      public class JUnit3TestMethodIsPublicVoidNoArg : junit.framework.TestCase() {
        fun testOne() { }
        public fun <warning descr="Test method 'testTwo()' is not declared 'public void'">testTwo</warning>(): Int { return 2 }
        public fun <warning descr="Test method 'testFour()' should probably not have parameters">testFour</warning>(i: Int) { println(i) }
        public fun testFive() { }
        private fun testSix(i: Int) { println(i) } //ignore when method doesn't look like test anymore
        companion object {
          @JvmStatic
          public fun <warning descr="Test method 'testThree()' should not be 'static'">testThree</warning>() { }
        }
      }
    """.trimIndent())
  }

  fun testJUnit4TestMethodIsPublicVoidNoArg() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Test
      public class JUnit4TestMethodIsPublicVoidNoArg {
        @Test fun testOne() { }
        @Test public fun <warning descr="Test method 'testTwo()' is not declared 'public void'">testTwo</warning>(): Int { return 2 }
        @Test public fun <warning descr="Test method 'testFour()' should probably not have parameters">testFour</warning>(i: Int) { }
        @Test public fun testFive() { }
        @Test public fun testMock(@mockit.Mocked s: String) { }
        companion object {
          @JvmStatic
          @Test public fun <warning descr="Test method 'testThree()' should not be 'static'">testThree</warning>() { }
        }
      }
    """.trimIndent())
  }

  fun testJUnit4RunWith() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runner.Runner::class)
      class JUnit4RunWith {
          @org.junit.Test public fun testMe(i: Int): Int { return -1 }
      }
    """.trimIndent())
  }
}