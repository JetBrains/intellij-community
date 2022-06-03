// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.diagnostic.telemetry.useWithScope
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.buildDistributions

object OpenSourceCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions().apply {
      // we cannot provide consistent build number for IDEA Community if it's built separately so use *.SNAPSHOT number to avoid confusion
      buildNumber = null
    }

    val context = createCommunityBuildContext(IdeaProjectLoaderUtil.guessCommunityHome(javaClass), options)
    BuildTasks.create(context).compileProjectAndTests(listOf("intellij.platform.jps.build"))
    // required because buildDistributions will trigger compilation of production modules
    context.options.incrementalCompilation = true
    buildDistributions(context)
    spanBuilder("Build standalone JPS").useWithScope {
      val jpsArtifactDir = context.paths.artifactDir.resolve("jps")
      buildCommunityStandaloneJpsBuilder(jpsArtifactDir, context)
    }
  }
}