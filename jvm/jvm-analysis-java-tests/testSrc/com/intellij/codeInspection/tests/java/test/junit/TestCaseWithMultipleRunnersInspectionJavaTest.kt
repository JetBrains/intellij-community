package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.jvm.analysis.internal.testFramework.test.junit.TestCaseWithMultipleRunnersInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage


class TestCaseWithMultipleRunnersInspectionJavaTest : TestCaseWithMultipleRunnersInspectionTestBase() {
  fun `test parent annotation`() {
    myFixture.addClass("""
      @org.junit.runner.RunWith(org.junit.runners.Suite.class)
      @org.junit.runners.Suite.SuiteClasses(Object.class)
      class ParentTestBaseSuite {
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.<warning descr="@RunWith annotation already exists in ParentTestBaseSuite class">RunWith</warning>(org.junit.runners.Parameterized.class)
      class MyTest extends ParentTestBaseSuite { 
        @org.junit.Test 
        public void test() {  
        } 
      }
    """.trimIndent())
  }

  fun `test interface annotation`() {
    myFixture.addClass("""
      @org.junit.runner.RunWith(org.junit.runners.Suite.class)
      @org.junit.runners.Suite.SuiteClasses(Object.class)
      interface ParentTestBaseSuite {
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.<warning descr="@RunWith annotation already exists in ParentTestBaseSuite class">RunWith</warning>(org.junit.runners.Parameterized.class)
      class MyTest implements ParentTestBaseSuite { 
        @org.junit.Test 
        public void test() {  
        } 
      }
    """.trimIndent())
  }

  fun `test inherited annotation1`() {
    myFixture.addClass("""
      @org.junit.runner.RunWith(org.junit.runners.Suite.class)
      @org.junit.runners.Suite.SuiteClasses(Object.class)
      interface SecondParentSuite {
      }
    """.trimIndent())

    myFixture.addClass("""
      interface FirstParent extends SecondParentSuite {
      }
    """.trimIndent())

    myFixture.addClass("""
      interface DummyInterface {
      }
    """.trimIndent())

    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.<warning descr="@RunWith annotation already exists in SecondParentSuite class">RunWith</warning>(org.junit.runners.Parameterized.class)
      class MyTest implements DummyInterface, FirstParent { 
        @org.junit.Test 
        public void test() {  
        } 
      }
    """.trimIndent())
  }

  fun `test inherited annotation2`() {
    myFixture.addClass("""
      @org.junit.runner.RunWith(org.junit.runners.Suite.class)
      @org.junit.runners.Suite.SuiteClasses(Object.class)
      class SecondParentSuite {
      }
    """.trimIndent())

    myFixture.addClass("""
      class FirstParent extends SecondParentSuite {
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.<warning descr="@RunWith annotation already exists in SecondParentSuite class">RunWith</warning>(org.junit.runners.Parameterized.class)
      class MyTest extends FirstParent { 
        @org.junit.Test 
        public void test() {  
        } 
      }
    """.trimIndent())
  }

  fun `test not inherited annotation`() {
    myFixture.addClass("""
      class ParentTestBase {
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized.class)
      class MyTest extends ParentTestBase { 
        @org.junit.Test 
        public void test() {  
        } 
      }
    """.trimIndent())
  }
}