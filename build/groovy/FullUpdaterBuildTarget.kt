// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.createCommunityBuildContext

object FullUpdaterBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val tasks = BuildTasks.create(createCommunityBuildContext(IdeaProjectLoaderUtil.guessCommunityHome(javaClass)))
    tasks.compileModules(listOf("updater"))
    tasks.buildFullUpdaterJar()
  }
}
