package com.intellij.codeInspection.deadCode

import com.intellij.jvm.analysis.internal.testFramework.deadCode.AssertJImplicitUsageProviderTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaAssertJImplicitUsageProviderTest : AssertJImplicitUsageProviderTestBase() {
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