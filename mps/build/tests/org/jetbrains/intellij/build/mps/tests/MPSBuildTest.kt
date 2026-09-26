// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.mps.tests

import com.intellij.platform.buildScripts.testFramework.runTestBuild
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.mps.MPSProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class MPSBuildTest {
  @Test
  fun build(testInfo: TestInfo) {
    runTestBuild(
      testInfo = testInfo,
      productProperties = MPSProperties(),
      homeDir = BuildPaths.MAYBE_ULTIMATE_HOME ?: BuildPaths.COMMUNITY_ROOT.communityRoot,
    )
  }
}
