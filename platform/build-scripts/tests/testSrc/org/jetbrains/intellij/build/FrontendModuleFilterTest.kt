// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.impl.createBuildContext
import org.junit.Test

class FrontendModuleFilterTest {
  @Test
  fun cachesValueForConcurrentAwaiters() {
    val filters = runBlocking(Dispatchers.Default) {
      val productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot).apply {
        embeddedFrontendRootModule = "intellij.idea.frontend.split"
      }
      val context = createBuildContext(projectHome = ULTIMATE_HOME, productProperties = productProperties, setupTracer = false)
      List(4) {
        async {
          context.getFrontendModuleFilter()
        }
      }.awaitAll()
    }

    val first = filters.first()
    filters.forEach {
      assertThat(it).isSameAs(first)
    }
  }
}
