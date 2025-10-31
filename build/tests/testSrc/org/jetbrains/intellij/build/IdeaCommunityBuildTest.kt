// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import OpenSourceCommunityInstallersBuildTarget
import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.runEssentialPluginsTest
import com.intellij.platform.buildScripts.testFramework.runTestBuild
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class IdeaCommunityBuildTest {
  @Test fun build(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot)
    runTestBuild(COMMUNITY_ROOT.communityRoot, productProperties, testInfo) {
      it.classOutDir = it.classOutDir ?: "$homePath/out/classes"
      /**
       * [com.intellij.platform.buildScripts.testFramework.customizeBuildOptionsForTest] modified [BuildOptions.buildStepsToSkip]
       * which should never be changed for this test because it's expected to match the production behavior
       */
      it.buildStepsToSkip =
        OpenSourceCommunityInstallersBuildTarget.OPTIONS.buildStepsToSkip +
        // no need to publish TeamCity artifacts from a test
        BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP
      // this step is disabled for all other build tests
      it.buildStepsToSkip -= BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP
    }
  }

  @Test fun jpsStandalone(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runBlocking(Dispatchers.Default) {
      runTestBuild(testInfo, context = {
        val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot)
        val options = createBuildOptionsForTest(productProperties, homePath, skipDependencySetup = true, testInfo)
        BuildContextImpl.createContext(homePath, productProperties, options, setupTracer = false)
      }) {
        buildCommunityStandaloneJpsBuilder(targetDir = it.paths.artifactDir.resolve("jps"), context = it)
      }
    }
  }

  @Test fun `essential plugins depend only on essential plugins`() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runEssentialPluginsTest(homePath, IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot), ProprietaryBuildTools.DUMMY)
  }
}
