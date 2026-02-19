// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.createCommunityBuildContext
import java.nio.file.Path

/**
 * Update a locally installed distribution from compiled classes.
 */
object OpenSourceCommunityUpdateFromSourcesBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
    options.buildStepsToSkip += listOf(
      BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP,
      BuildOptions.SOURCES_ARCHIVE_STEP,
    )
    if (!System.getProperty("intellij.build.local.plugins.repository", "false").toBoolean()) {
      options.buildStepsToSkip += listOf(
        BuildOptions.PROVIDED_MODULES_LIST_STEP,
        BuildOptions.NON_BUNDLED_PLUGINS_STEP,
      )
    }

    val distOutputRelativePath = System.getProperty("distOutputRelativePath")!!

    // when IDEA CE is updated from IDEA, a UE sources project should be loaded from IDEA UE directory
    val projectHome = System.getProperty("devIdeaHome")?.let { Path.of(it) } ?: COMMUNITY_ROOT.communityRoot
    runBlocking(Dispatchers.Default) {
      createBuildTasks(createCommunityBuildContext(options, projectHome))
        .buildUnpackedDistribution(options.outRootDir!!.resolve(distOutputRelativePath), includeBinAndRuntime = true)
    }
  }
}
