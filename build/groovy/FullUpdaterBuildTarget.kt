// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.*

object FullUpdaterBuildTarget {
  private const val UPDATER_MODULE_NAME = "intellij.platform.updater"

  @JvmStatic
  fun main(args: Array<String>) {
    val context = createCommunityBuildContext(IdeaProjectLoaderUtil.guessCommunityHome(javaClass))

    context.messages.block("Building artifact") {
      val tasks = BuildTasks.create(context)
      tasks.compileModules(listOf(UPDATER_MODULE_NAME))
      tasks.buildFullUpdaterJar()
    }

    context.messages.block("Testing") {
      val options = TestingOptions()
      options.testGroups = "UPDATER_TESTS"
      options.mainModule = UPDATER_MODULE_NAME
      TestingTasks.create(context, options)
        .runTests(listOf("-Dintellij.build.test.ignoreFirstAndLastTests=true"))
    }
  }
}
