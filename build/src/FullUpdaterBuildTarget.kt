// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.diagnostic.telemetry.useWithScope
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder

object FullUpdaterBuildTarget {
  private const val UPDATER_MODULE_NAME = "intellij.platform.updater"

  @JvmStatic
  fun main(args: Array<String>) {
    val context = createCommunityBuildContext(IdeaProjectLoaderUtil.guessCommunityHome(javaClass))

    spanBuilder("build updater artifact").useWithScope {
      val tasks = BuildTasks.create(context)
      tasks.compileModules(listOf(UPDATER_MODULE_NAME))
      tasks.buildFullUpdaterJar()
    }

    spanBuilder("test updater").useWithScope {
      val options = TestingOptions()
      options.testGroups = "UPDATER_TESTS"
      options.mainModule = UPDATER_MODULE_NAME
      TestingTasks.create(context, options)
        .runTests(listOf("-Dintellij.build.test.ignoreFirstAndLastTests=true"))
    }
  }
}
