// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.intellij.diagnostic.telemetry.useWithScope2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.buildDistributions

object OpenSourceCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions().apply {
      // we cannot provide consistent build number for IDEA Community if it's built separately so use *.SNAPSHOT number to avoid confusion
      buildNumber = null

      // do not bother external users about clean/incremental
      // just remove out/ directory for clean build
      incrementalCompilation = true
      useCompiledClassesFromProjectOutput = false
      buildStepsToSkip += BuildOptions.MAC_SIGN_STEP
    }

    val context = createCommunityBuildContext(IdeaProjectLoaderUtil.guessCommunityHome(javaClass), options)
    BuildTasks.create(context).compileProjectAndTests(listOf("intellij.platform.jps.build.tests"))
    runBlocking(Dispatchers.Default) {
      buildDistributions(context)
      spanBuilder("build standalone JPS").useWithScope2 {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context = context)
      }
    }
  }
}