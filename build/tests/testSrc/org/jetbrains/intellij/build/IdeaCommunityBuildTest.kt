// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.application.PathManager
import org.jetbrains.intellij.build.testFramework.runTestBuild
import org.junit.Test

class IdeaCommunityBuildTest {
  @Test
  fun testBuild() {
    val homePath = PathManager.getHomePathFor(javaClass)!!
    runTestBuild(
      homePath = homePath,
      productProperties = IdeaCommunityProperties(homePath),
      buildTools = ProprietaryBuildTools.DUMMY,
      communityHomePath = homePath,
    )
  }
}