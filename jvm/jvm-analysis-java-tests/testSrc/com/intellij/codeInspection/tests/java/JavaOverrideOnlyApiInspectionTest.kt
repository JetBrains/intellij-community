package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.OverrideOnlyApiInspectionTestBase

class JavaOverrideOnlyApiInspectionTest : OverrideOnlyApiInspectionTestBase() {

  fun `test invocations`() {
    myFixture.testHighlighting("plugin/JavaCode.java")
  }

  fun `test delegation`() {
    myFixture.testHighlighting("plugin/JavaCodeDelegate.java")
  }

  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
