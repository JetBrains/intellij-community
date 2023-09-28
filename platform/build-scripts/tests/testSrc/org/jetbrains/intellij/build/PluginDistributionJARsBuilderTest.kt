// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.createIdeClassPath
import org.jetbrains.intellij.build.impl.createPlatformLayout
import org.junit.Test

class PluginDistributionJARsBuilderTest {
  @Test
  fun verifyStableClasspathOrder() {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    val productProperties = IdeaCommunityProperties(communityHome.communityRoot)
    runBlocking(Dispatchers.Default) {
      val context = BuildContextImpl.createContext(communityHome, communityHome.communityRoot, productProperties)
      val ideClasspath1 = createIdeClassPath(createPlatformLayout(pluginsToPublish = emptySet(), context = context), context)
      val ideClasspath2 = createIdeClassPath(createPlatformLayout(pluginsToPublish = emptySet(), context = context), context)
      assertThat(ideClasspath1).isEqualTo(ideClasspath2)
    }
  }
}
