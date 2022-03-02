// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import com.intellij.util.SystemProperties
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.IdeaCommunityBuilder
import org.jetbrains.intellij.build.IdeaProjectLoaderUtil

/**
 * Update locally installed distribution from compiled classes
 */
object OpenSourceCommunityUpdateFromSourcesBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions()
    options.useCompiledClassesFromProjectOutput = true
    //  options.buildStepsToSkip << BuildOptions.SVGICONS_PREBUILD_STEP
    options.buildStepsToSkip.add(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)
    options.buildStepsToSkip.add(BuildOptions.SOURCES_ARCHIVE_STEP)
    if (!SystemProperties.getBooleanProperty("intellij.build.local.plugins.repository", false)) {
      options.buildStepsToSkip.add(BuildOptions.PROVIDED_MODULES_LIST_STEP)
      options.buildStepsToSkip.add(BuildOptions.NON_BUNDLED_PLUGINS_STEP)
    }

    val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass).toString()
    val distOutputRelativePath = System.getProperty("distOutputRelativePath")!!

    //when IDEA CE is updated from IDEA UE sources project should be loaded from IDEA UE directory
    val projectHome = System.getProperty("devIdeaHome", communityHome)
    IdeaCommunityBuilder(communityHome, options, projectHome)
      .buildUnpackedDistribution("${options.outputRootPath}/$distOutputRelativePath")
  }
}