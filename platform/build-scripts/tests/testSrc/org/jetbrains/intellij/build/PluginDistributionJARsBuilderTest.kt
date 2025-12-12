// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.createIdeClassPath
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.junit.Test

class PluginDistributionJARsBuilderTest {
  @Test
  fun verifyStableClasspathOrder() {
    val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot)
    runBlocking(Dispatchers.Default) {
      val context = createBuildContext(COMMUNITY_ROOT.communityRoot, productProperties)
      val ideClasspath1 = createIdeClassPath(createPlatformLayout(context = context), context)
      val ideClasspath2 = createIdeClassPath(createPlatformLayout(context = context), context)
      assertThat(ideClasspath1).isEqualTo(ideClasspath2)
    }
  }
}
