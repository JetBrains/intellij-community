// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.runEssentialPluginsTest
import com.intellij.platform.buildScripts.testFramework.runTestBuild
import com.intellij.platform.buildScripts.testFramework.spanName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class IdeaCommunityBuildTest {
  @Test
  fun build(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot)
    runTestBuild(
      homeDir = homePath,
      traceSpanName = testInfo.spanName,
      productProperties = productProperties,
    ) {
      it.classOutDir = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY) ?: "$homePath/out/classes"
    }
  }

  @Test
  fun jpsStandalone(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runBlocking(Dispatchers.Default) {
      val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot)
      val options = createBuildOptionsForTest(productProperties = productProperties, homeDir = homePath, skipDependencySetup = true)
      val context = BuildContextImpl.createContext(
        projectHome = homePath,
        productProperties = productProperties,
        setupTracer = false,
        options = options,
      )
      runTestBuild(context = context, traceSpanName = testInfo.spanName) {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context = context)
      }
    }
  }

  @Test
  fun `essential plugins depend only on essential plugins`() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    runEssentialPluginsTest(homePath = homePath,
                            productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
                            buildTools = ProprietaryBuildTools.DUMMY)
  }
}