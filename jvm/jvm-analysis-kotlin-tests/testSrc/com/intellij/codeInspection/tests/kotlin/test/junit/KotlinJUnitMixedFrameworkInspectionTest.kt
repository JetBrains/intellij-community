package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMixedFrameworkInspectionTestBase

class KotlinJUnitMixedFrameworkInspectionTest : JUnitMixedFrameworkInspectionTestBase() {
  fun `test no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit3Test : junit.framework.TestCase() {
        public fun testFoo() { }
      }      
    """.trimIndent(), fileName = "JUnit3Test")
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit4Test {
        @org.junit.Test
        public fun testFoo() { }
      }
    """.trimIndent(), fileName = "JUnit4Test")
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit5Test {
        @org.junit.jupiter.api.Test
        public fun testFoo() { }
      }      
    """.trimIndent(), fileName = "JUnit5Test")
    myFixture.addFileToProject("JUnit5TestCase.kt", """
      open class JUnit5TestCase {
        @org.junit.jupiter.api.BeforeAll
        public fun beforeAll() { }
      }      
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit5Test : JUnit5TestCase() {
        @org.junit.jupiter.api.Test
        public fun testFoo() { }
      }      
    """.trimIndent(), fileName = "JUnit5Test")
  }

  fun `test highlighting junit 3 test case with junit 4 test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class MyTest : junit.framework.TestCase() {
        @org.junit.Test
        public fun <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 3 TestCase">testFoo</warning>() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }

  fun `test highlighting junit 3 test case with junit 5 test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class MyTest : junit.framework.TestCase() {
        @org.junit.jupiter.api.Test
        public fun <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 3 TestCase">testFoo</warning>() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }

  fun `test junit 3 test case with junit 4 test remove annotation quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      public class MyTest : junit.framework.TestCase() {
        @org.junit.Test
        public fun test<caret>Foo() { }
      }
    """.trimIndent(), """
      public class MyTest : junit.framework.TestCase() {
        public fun testFoo() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@Test' annotation")
  }

  fun `test junit 3 test case with junit 4 test remove test annotation and rename quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      public class MyTest : junit.framework.TestCase() {
        @org.junit.Test
        public fun f<caret>oo() { }
      }
    """.trimIndent(), """
      public class MyTest : junit.framework.TestCase() {
        public fun testFoo() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@Test' annotation")
  }

  fun `test junit 3 test case with junit 4 test remove ignore annotation and rename quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      public class MyTest : junit.framework.TestCase() {
        @org.junit.Ignore
        public fun testF<caret>oo() { }
      }
    """.trimIndent(), """
      public class MyTest : junit.framework.TestCase() {
        public fun _testFoo() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Remove '@Ignore' annotation")
  }

  fun `test highlighting junit 4 test case with junit 5 test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class MyTest {
        @org.junit.jupiter.api.Test
        public fun <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 4 TestCase">testFoo</warning>() { }
        
        @org.junit.Test
        public fun testBar() { }
        
        @org.junit.Test
        public fun testFooBar() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }

  fun `test highlighting junit 5 test case with junit 4 test`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class MyTest {
        @org.junit.Test
        public fun <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 5 TestCase">testFoo</warning>() { }
        
        @org.junit.jupiter.api.Test
        public fun testBar() { }
        
        @org.junit.jupiter.api.Test
        public fun testFooBar() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }


  fun `test junit 5 test case with junit 4 quickfix`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      public class MyTest {
        @org.junit.Test
        public fun test<caret>Foo() { }
        
        @org.junit.jupiter.api.Test
        public fun testBar() { }
        
        @org.junit.jupiter.api.Test
        public fun testFooBar() { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Test
      
      public class MyTest {
        @Test
        public fun testFoo() { }
        
        @org.junit.jupiter.api.Test
        public fun testBar() { }
        
        @org.junit.jupiter.api.Test
        public fun testFooBar() { }
      }
    """.trimIndent(), fileName = "MyTest", hint = "Migrate to JUnit 5")
  }

  fun `test highlighting junit 5 test case with junit 4 super class`() {
    myFixture.addFileToProject("JUnit4TestCase.kt", """
      open class JUnit4TestCase {
        @org.junit.BeforeClass
        public fun beforeClass() { }
      }      
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class MyTest : JUnit4TestCase() {
        @org.junit.jupiter.api.Test
        public fun <warning descr="Method 'testFoo()' annotated with '@Test' inside class extending JUnit 4 TestCase">testFoo</warning>() { }
      }
    """.trimIndent(), fileName = "MyTest")
  }
}