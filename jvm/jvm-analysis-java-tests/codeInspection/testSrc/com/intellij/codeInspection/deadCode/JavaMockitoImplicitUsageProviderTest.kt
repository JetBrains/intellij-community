package com.intellij.codeInspection.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.mockitoImplicitUsageProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmImplicitUsageProviderFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaMockitoImplicitUsageProviderTest {
  companion object {
    private val projectFixture = mockitoImplicitUsageProjectFixture()
  }

  private val myFixture by jvmImplicitUsageProviderFixture(projectFixture)

  @Test
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