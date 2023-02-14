// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ifs

import com.intellij.testFramework.PlatformTestUtil
import java.io.File

object JavaSuggestersTestUtils {
  val testDataPath: String
    get() = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/java/java-features-trainer/testData"
}