// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil
import org.jetbrains.intellij.build.createCommunityBuildContext

object FullUpdaterBuildTarget {
  private const val UPDATER_MODULE_NAME = "intellij.platform.updater"

  @JvmStatic
  fun main(args: Array<String>) {
    val context = createCommunityBuildContext(IdeaProjectLoaderUtil.guessCommunityHome(javaClass))
    val tasks = BuildTasks.create(context)
    tasks.compileModules(listOf(UPDATER_MODULE_NAME))
    runBlocking(Dispatchers.Default) {
      tasks.buildFullUpdaterJar()
    }
  }
}
