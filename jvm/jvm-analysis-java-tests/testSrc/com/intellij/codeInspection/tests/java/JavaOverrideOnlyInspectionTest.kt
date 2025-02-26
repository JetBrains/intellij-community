package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.OverrideOnlyInspectionTestBase
import org.junit.Test
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner::class)
class JavaOverrideOnlyInspectionTest : OverrideOnlyInspectionTestBase() {

  @Test
  fun `test invocations`() {
    myFixture.testHighlighting("plugin/JavaCode.java")
  }

  @Test
  fun `test delegation`() {
    myFixture.testHighlighting("plugin/DelegateJavaCode.java")
  }

  @Test
  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
