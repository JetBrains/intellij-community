// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.IdeaCommunityBuilder
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil

object FullUpdaterBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    IdeaCommunityBuilder(communityHome).buildFullUpdater()
  }
}