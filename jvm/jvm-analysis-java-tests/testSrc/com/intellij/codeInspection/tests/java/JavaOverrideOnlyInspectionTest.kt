package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.OverrideOnlyInspectionTestBase

class JavaOverrideOnlyInspectionTest : OverrideOnlyInspectionTestBase() {

  fun `test invocations`() {
    myFixture.testHighlighting("plugin/JavaCode.java")
  }

  fun `test delegation`() {
    myFixture.testHighlighting("plugin/DelegateJavaCode.java")
  }

  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
