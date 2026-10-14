package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.OverrideOnlyApiInspectionTestBase
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil

class JavaOverrideOnlyApiInspectionTest : OverrideOnlyApiInspectionTestBase() {
  override fun getTestDataPath(): String {
    if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//jvm/jvm-analysis-kotlin-tests-shared:testData")
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString() + "/codeInspection/overrideOnly"
    }
    return super.getTestDataPath()
  }

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
