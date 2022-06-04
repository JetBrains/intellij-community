// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder

object KotlinPluginBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    KotlinPluginBuilder(communityHome, communityHome, IdeaCommunityProperties(communityHome)).build()
  }
}