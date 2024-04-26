package com.intellij.codeInspection.tests.kotlin.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.EasyMockImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class KotlinEasyMockImplicitUsageProviderTest : EasyMockImplicitUsageProviderTestBase() {
  fun `test implicit usage for mocked field`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MyEasyMockTest {
        @org.easymock.Mock
        private lateinit var myFoo: String
      
        init {
          System.out.println(myFoo)
        }
        
         @org.junit.Test
         fun testName() { }
      }
    """.trimIndent(), fileName = "MyEasyMockTest")
  }
}