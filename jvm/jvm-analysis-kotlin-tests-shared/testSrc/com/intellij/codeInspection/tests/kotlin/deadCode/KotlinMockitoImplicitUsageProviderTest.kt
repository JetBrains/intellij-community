package com.intellij.codeInspection.tests.kotlin.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.MockitoImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider

abstract class KotlinMockitoImplicitUsageProviderTest : MockitoImplicitUsageProviderTestBase(), ExpectedPluginModeProvider {
  fun `test implicit usage for mocked field`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MyMockitoTest {
        @org.mockito.Mock
        private lateinit var myFoo: String
        
        init {
          System.out.println(myFoo)
        }
        
         @org.junit.Test
         fun testName() { }
      }
    """.trimIndent(), fileName = "MyMockitoTest")
  }
}