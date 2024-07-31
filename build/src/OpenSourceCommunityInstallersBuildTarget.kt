// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.buildCommunityStandaloneJpsBuilder
import org.jetbrains.intellij.build.createCommunityBuildContext
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.useWithScope

internal object OpenSourceCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions().apply {
      // do not bother external users about clean/incremental
      // just remove out/ directory for clean build
      incrementalCompilation = true
      useCompiledClassesFromProjectOutput = false
      buildStepsToSkip += BuildOptions.MAC_SIGN_STEP
    }

    runBlocking(Dispatchers.Default) {
      val context = createCommunityBuildContext(options)
      CompilationTasks.create(context).compileModules(moduleNames = null, includingTestsInModules = listOf("intellij.platform.jps.build.tests"))
      buildDistributions(context)
      spanBuilder("build standalone JPS").useWithScope {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context = context)
      }
    }
  }
}