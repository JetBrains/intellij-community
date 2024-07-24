package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestOnlyInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinTestOnlyInspectionTest : TestOnlyInspectionTestBase(), KotlinPluginModeProvider {
  fun `test @TestOnly on use-site targets`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      
      import org.jetbrains.annotations.TestOnly
      import org.jetbrains.annotations.VisibleForTesting
        
      // IDEA-269740 need better support for UAST properties
      @get:[TestOnly VisibleForTesting]
      val x = 0
        
      @get:[TestOnly]
      val y = 0
        
      @get:TestOnly
      val z = 0
        
      fun doSomething(q: Int) = q
        
      fun main() {
        doSomething(<warning descr="Test-only method is called in production code">y</warning>)
        doSomething(<warning descr="Test-only method is called in production code">z</warning>)
      }
    """.trimIndent())
  }

  fun `test @TestOnly in production code`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      package test
      
      import org.jetbrains.annotations.TestOnly
      import org.jetbrains.annotations.VisibleForTesting

      class TestOnlyTest @TestOnly constructor() {
        val nonTestField = 0

        var aField = 0
          @TestOnly get() = field

        @TestOnly
        fun aMethod(x: Int): Int = x

        @TestOnly
        @<warning descr="@VisibleForTesting makes little sense on @TestOnly code">VisibleForTesting</warning>
        fun aStringMethod(): String = "Foo"
      }

      /**
       * [TestOnlyTest.aMethod]
       * [testOnly]
       */
      fun main() {
        val foo1 = <warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>()
        val foo2 = test.<warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>()
        val foo3 = <warning descr="Test-only class is referenced in production code">TestOnlyTest</warning>().nonTestField
        val bar = foo1.<warning descr="Test-only method is called in production code">aField</warning>
        foo1.<warning descr="Test-only method is called in production code">aMethod</warning>(bar)
        TestOnlyTest::<warning descr="Test-only method is called in production code">aMethod</warning>.invoke(foo2, foo3)
        test.TestOnlyTest::<warning descr="Test-only method is called in production code">aMethod</warning>.invoke(foo2, foo3)
        <warning descr="Test-only method is called in production code">testOnly</warning>()
      }

      @TestOnly
      fun testOnly() {
        val foo1 = TestOnlyTest()
        val foo2 = test.TestOnlyTest()
        val foo3 = TestOnlyTest().nonTestField
        val bar = foo1.aField
        foo1.aMethod(bar)
        TestOnlyTest::aMethod.invoke(foo2, foo3)
        test.TestOnlyTest::aMethod.invoke(foo2, foo3)
      }
    """.trimIndent())
  }

  fun `test @VisibleForTesting in production code`() {
    myFixture.addFileToProject("VisibleForTestingTestApi.kt", """
      package testapi
      
      import org.jetbrains.annotations.VisibleForTesting

      object VisibleForTestingTestApi {
        var foo = 0 @VisibleForTesting get() = field

        @VisibleForTesting
        fun bar() { }
      }
    """.trimIndent())

    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.jetbrains.annotations.VisibleForTesting
      import testapi.VisibleForTestingTestApi
      
      object VisibleForTestingTest {
        val foobar = 0
          @VisibleForTesting get() = field

        fun main() {
          foobar
          VisibleForTestingTestApi.<warning descr="Test-only method is called in production code">foo</warning>
          VisibleForTestingTestApi.<warning descr="Test-only method is called in production code">bar</warning>()
        }
      }
    """.trimIndent())
  }
}