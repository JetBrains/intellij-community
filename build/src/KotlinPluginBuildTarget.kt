// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder

internal object KotlinPluginBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
    runBlocking(Dispatchers.Default) {
      KotlinPluginBuilder.build(communityHome = communityHome,
                                home = communityHome.communityRoot,
                                properties = IdeaCommunityProperties(communityHome.communityRoot))
    }
  }
}
