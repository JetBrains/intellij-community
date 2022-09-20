// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.testFramework.createBuildContext
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
      val context = createBuildContext(
        homePath = homePath,
        productProperties = IdeaCommunityProperties(communityHome.communityRoot),
        skipDependencySetup = true,
        communityHomePath = communityHome,
      )
      val outDir = context.paths.buildOutputDir

      try {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context = context)
      }
      finally {
        withContext(Dispatchers.IO + NonCancellable) {
          NioFiles.deleteRecursively(outDir)
        }
      }
    }
  }
}