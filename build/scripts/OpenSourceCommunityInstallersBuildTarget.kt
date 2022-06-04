// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.IdeaCommunityBuilder
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil

object OpenSourceCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions().apply {
      // we cannot provide consistent build number for IDEA Community if it's built separately so use *.SNAPSHOT number to avoid confusion
      buildNumber = null
    }

    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    IdeaCommunityBuilder(communityHome, options).buildDistributions()
  }
}