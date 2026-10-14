package com.intellij.jvm.analysis

import com.intellij.jvm.analysis.internal.testFramework.JvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.common.BazelTestUtil
import java.io.File

object JavaJvmAnalysisTestUtil {
  @JvmStatic
  val testDataPath: String
    get() {
      if (BazelTestUtil.isUnderBazelTest) {
        val label = BazelLabel.fromString("@community//jvm/jvm-analysis-java-tests:testData")
        return BazelTestUtil.getFileFromBazelRuntime(label).toAbsolutePath().toString()
      }
      val communityPath = PathManager.getCommunityHomePath().replace(File.separatorChar, '/')
      return "$communityPath${JvmAnalysisTestUtil.MODULE_PATH}/jvm-analysis-java-tests/testData"
    }
}