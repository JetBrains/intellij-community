// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.createCommunityBuildContext

object FullUpdaterBuildTarget {
  private const val UPDATER_MODULE_NAME = "intellij.platform.updater"

  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val context = createCommunityBuildContext()
      val tasks = createBuildTasks(context)
      tasks.compileModules(listOf(UPDATER_MODULE_NAME))
      tasks.buildFullUpdaterJar()
    }
  }
}
