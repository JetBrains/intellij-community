package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.internal.testFramework.NonExtendableApiInspectionTestBase
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil

class JavaNonExtendableApiInspectionTest : NonExtendableApiInspectionTestBase() {
  override fun getTestDataPath(): String {
    if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//jvm/jvm-analysis-kotlin-tests-shared:testData")
      return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString() + "/codeInspection/nonExtendableApiUsage"
    }
    return super.getTestDataPath()
  }

  fun `test extensions`() {
    myFixture.testHighlighting("plugin/javaExtensions.java")
  }

  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
