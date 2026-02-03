// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.kotlin.CommunityKotlinPluginBuilder

internal object KotlinPluginBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      CommunityKotlinPluginBuilder.build(home = COMMUNITY_ROOT.communityRoot,
                                         properties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot))
    }
  }
}
