package com.intellij.codeInspection.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.MockitoImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaMockitoImplicitUsageProviderTest : MockitoImplicitUsageProviderTestBase() {
  fun `test implicit usage for mocked field`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyMockitoTest {
        @org.mockito.Mock
        private String myFoo;
        
        {
          System.out.println(myFoo);
        }
        
         @org.junit.Test
         public void testName() throws Exception { }
      }
    """.trimIndent(), fileName = "MyMockitoTest")
  }
}