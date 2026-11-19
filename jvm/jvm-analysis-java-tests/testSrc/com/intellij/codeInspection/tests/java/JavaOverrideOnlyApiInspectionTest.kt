package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.overrideOnlyApiInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.overrideOnlyApiProjectFixture
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val testDataPath: String = if (BazelTestUtil.isUnderBazelTest) {
  val label = BazelLabel.fromString("@community//jvm/jvm-analysis-kotlin-tests-shared:testData")
  BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString() + "/codeInspection/overrideOnly"
}
else {
  FileUtil.toSystemIndependentName(PathManager.getCommunityHomePath()) + "/jvm/jvm-analysis-kotlin-tests-shared/testData/codeInspection/overrideOnly"
}

@TestApplication
class JavaOverrideOnlyApiInspectionTest {
  companion object {
    private val projectFixture = overrideOnlyApiProjectFixture(testDataPath)
  }

  private val myFixture by overrideOnlyApiInspectionFixture(projectFixture, testDataPath)

  @Test
  fun `test invocations`() {
    myFixture.testHighlighting("plugin/JavaCode.java")
  }

  @Test
  fun `test delegation`() {
    myFixture.testHighlighting("plugin/JavaCodeDelegate.java")
  }

  @Test
  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
