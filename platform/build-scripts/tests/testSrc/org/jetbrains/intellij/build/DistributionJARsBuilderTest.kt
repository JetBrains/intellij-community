// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.DistributionBuilderState
import org.jetbrains.intellij.build.impl.DistributionJARsBuilder
import org.junit.Test

class DistributionJARsBuilderTest {
  @Test
  fun verifyStableClasspathOrder() {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    val productProperties = IdeaCommunityProperties(communityHome.communityRoot)
    runBlocking(Dispatchers.Default) {
      val context = BuildContextImpl.createContext(communityHome, communityHome.communityRoot, productProperties)
      val ideClasspath1 = DistributionJARsBuilder(DistributionBuilderState(emptySet(), context)).createIdeClassPath(context)
      val ideClasspath2 = DistributionJARsBuilder(DistributionBuilderState(emptySet(), context)).createIdeClassPath(context)
      assertThat(ideClasspath1).isEqualTo(ideClasspath2)
    }
  }
}