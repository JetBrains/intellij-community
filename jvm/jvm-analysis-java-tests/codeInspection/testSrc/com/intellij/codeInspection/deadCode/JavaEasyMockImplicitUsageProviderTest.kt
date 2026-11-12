package com.intellij.codeInspection.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.EasyMockImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaEasyMockImplicitUsageProviderTest : EasyMockImplicitUsageProviderTestBase() {
  fun `test implicit usage for mocked field`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      public class MyEasyMockTest {
        @org.easymock.Mock
        private String myFoo;
      
        {
          System.out.println(myFoo);
        }
        
         @org.junit.Test
         public void testName() throws Exception { }
      }
    """.trimIndent(), fileName = "MyEasyMockTest")
  }
}