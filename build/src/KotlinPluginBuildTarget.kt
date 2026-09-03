// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.kotlin.CommunityKotlinPluginBuilder
import org.jetbrains.intellij.build.runBlockingOnVirtualThreads

internal object KotlinPluginBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlockingOnVirtualThreads {
      CommunityKotlinPluginBuilder.build(home = COMMUNITY_ROOT.communityRoot,
                                         properties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot))
    }
  }
}
