// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import com.intellij.platform.buildScripts.testFramework.createBuildOptionsForTest
import com.intellij.platform.buildScripts.testFramework.runTestBuild
import com.intellij.platform.buildScripts.testFramework.spanName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class IdeaCommunityBuildTest {
  @Test
  fun build(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val communityHomePath = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    val productProperties = IdeaCommunityProperties(communityHomePath.communityRoot)
    runTestBuild(
      homePath = homePath,
      traceSpanName = testInfo.spanName,
      communityHomePath = communityHomePath,
      productProperties = productProperties,
    ) {
      it.classesOutputDirectory = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)
                                  ?: "$homePath/out/classes"
    }
  }

  @Test
  fun jpsStandalone(testInfo: TestInfo) {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    runBlocking(Dispatchers.Default) {
      val productProperties = IdeaCommunityProperties(communityHome.communityRoot)
      val options = createBuildOptionsForTest(productProperties = productProperties, skipDependencySetup = true)
      val context = BuildContextImpl.createContext(communityHome = communityHome,
                                                   projectHome = homePath,
                                                   productProperties = productProperties,
                                                   setupTracer = false,
                                                   options = options)
      runTestBuild(context = context, traceSpanName = testInfo.spanName) {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context = context)
      }
    }
  }
}