// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.testFramework.createBuildOptionsForTest
import org.jetbrains.intellij.build.testFramework.runTestBuild
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

class IdeaCommunityBuildTest {
  @Test
  fun testBuild() {
    val homePath = PathManager.getHomeDirFor(javaClass)!!
    val communityHomePath = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    runTestBuild(
      homePath = homePath,
      communityHomePath = communityHomePath,
      productProperties = IdeaCommunityProperties(communityHomePath.communityRoot),
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
                                                   options = options)
      runTestBuild(context) {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context = context)
      }
    }
  }
}