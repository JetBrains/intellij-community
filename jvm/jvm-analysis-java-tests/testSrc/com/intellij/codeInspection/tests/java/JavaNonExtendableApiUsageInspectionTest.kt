package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.NonExtendableApiUsageTestBase

class JavaNonExtendableApiUsageInspectionTest : NonExtendableApiUsageTestBase() {

  fun `test extensions`() {
    myFixture.testHighlighting("plugin/javaExtensions.java")
  }

  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
