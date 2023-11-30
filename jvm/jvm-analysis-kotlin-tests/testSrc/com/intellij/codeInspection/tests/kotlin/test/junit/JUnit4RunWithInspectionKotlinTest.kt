package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.jvm.analysis.internal.testFramework.test.junit.JUnit4RunWithInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JUnit4RunWithInspectionKotlinTest : JUnit4RunWithInspectionTestBase() {
  fun `test parent annotation`() {
    myFixture.addClass("""
      @org.junit.runner.RunWith(org.junit.runners.Suite.class)
      @org.junit.runners.Suite.SuiteClasses(Object.class)
      public class ParentTestBaseSuite {
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.runner.<warning descr="@RunWith annotation already exists in ParentTestBaseSuite class">RunWith</warning>(org.junit.runners.Parameterized::class)
      class MyTest : ParentTestBaseSuite() { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }

  fun `test inherited annotation`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.Suite::class)
      @org.junit.runners.Suite.SuiteClasses(Object::class)
      open class SecondParent {
      }
      
      open class FirstParent : SecondParent() {
      }
      
      @org.junit.runner.<warning descr="@RunWith annotation already exists in SecondParent class">RunWith</warning>(org.junit.runners.Parameterized::class)
      class MyTest : FirstParent() { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }

  fun `test not inherited annotation`() {
    myFixture.addClass("""
      public class ParentTestBase {
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.Parameterized::class)
      class MyTest : ParentTestBase() { 
        @org.junit.Test 
        fun test() {  
        } 
      }
    """.trimIndent())
  }
}