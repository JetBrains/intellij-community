// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.testFramework.runTestBuild
import org.junit.jupiter.api.Test

class IdeaCommunityBuildTest {
  @Test
  fun testBuild() {
    val homePath = PathManager.getHomePathFor(javaClass)!!
    val communityHomePath = "$homePath/community"
    runTestBuild(
      homePath = communityHomePath, communityHomePath = communityHomePath,
      productProperties = IdeaCommunityProperties(communityHomePath),
      buildTools = ProprietaryBuildTools.DUMMY
    ) {
      it.projectClassesOutputDirectory = System.getProperty(BuildOptions.PROJECT_CLASSES_OUTPUT_DIRECTORY_PROPERTY)
                                         ?: "$homePath/out/classes"
    }
  }
}