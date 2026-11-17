package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.nonExtendableApiInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.nonExtendableApiProjectFixture
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private val testDataPath: String = if (BazelTestUtil.isUnderBazelTest) {
  val label = BazelLabel.fromString("@community//jvm/jvm-analysis-kotlin-tests-shared:testData")
  BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString() + "/codeInspection/nonExtendableApiUsage"
}
else {
  FileUtil.toSystemIndependentName(PathManager.getCommunityHomePath()) + "/jvm/jvm-analysis-kotlin-tests-shared/testData/codeInspection/nonExtendableApiUsage"
}

@TestApplication
class JavaNonExtendableApiInspectionTest {
  companion object {
    private val projectFixture = nonExtendableApiProjectFixture(testDataPath)
  }

  private val myFixture by nonExtendableApiInspectionFixture(projectFixture, testDataPath)

  @Test
  fun `test extensions`() {
    myFixture.testHighlighting("plugin/javaExtensions.java")
  }

  @Test
  fun `test invalid annotation targets`() {
    myFixture.testHighlighting("plugin/JavaInvalidAnnotationTargets.java")
  }
}
