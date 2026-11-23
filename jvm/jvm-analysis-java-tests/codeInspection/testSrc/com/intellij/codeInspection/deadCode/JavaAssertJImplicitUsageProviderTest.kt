package com.intellij.codeInspection.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.assertJImplicitUsageProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmImplicitUsageProviderFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaAssertJImplicitUsageProviderTest {
  companion object {
    private val projectFixture = assertJImplicitUsageProjectFixture()
  }

  private val myFixture by jvmImplicitUsageProviderFixture(projectFixture)

  @Test
  fun `test inject soft assertion implicit usage provider`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      @org.junit.jupiter.api.extension.ExtendWith(org.assertj.core.api.junit.jupiter.SoftAssertionsExtension.class)
      public class TestClass {
          @org.assertj.core.api.junit.jupiter.InjectSoftAssertions
          private org.assertj.core.api.SoftAssertions softAssertions;

          @org.junit.jupiter.api.Test
          public void doSomething() {
              softAssertions.assertThat("string").isEqualTo("string");
          }
      }
    """.trimIndent(), fileName = "TestClass")
  }
}