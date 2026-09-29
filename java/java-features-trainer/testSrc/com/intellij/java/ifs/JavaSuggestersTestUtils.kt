// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ifs

import com.intellij.platform.bazel.runfiles.BazelLabel
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.BazelTestUtil
import java.io.File
import kotlin.io.path.absolutePathString

object JavaSuggestersTestUtils {
  val testDataPath: String
    get() = if (BazelTestUtil.isUnderBazelTest) {
      val label = BazelLabel.fromString("@community//java/java-features-trainer:testData")
      BazelTestUtil.getFileFromBazelRuntime(label).absolutePathString()
    }
    else {
      PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-features-trainer/testData"
    }
}