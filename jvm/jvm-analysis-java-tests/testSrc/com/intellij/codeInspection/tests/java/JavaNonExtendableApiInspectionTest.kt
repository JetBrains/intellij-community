package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.NonExtendableApiInspectionTestBase

class JavaNonExtendableApiInspectionTest : NonExtendableApiInspectionTestBase() {

  fun `test extensions`() {
    myFixture.testHighlighting("plugin/javaExtensions.java")
  }

  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
