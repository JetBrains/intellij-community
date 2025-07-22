// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use

internal object OpenSourceCommunityInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    val options = BuildOptions().apply {
      // do not bother external users about clean/incremental
      // just remove out/ directory for clean build
      incrementalCompilation = true
      useCompiledClassesFromProjectOutput = false
      buildStepsToSkip += BuildOptions.MAC_SIGN_STEP
      buildStepsToSkip += BuildOptions.WIN_SIGN_STEP
      if (OsFamily.currentOs == OsFamily.MACOS) {
        // generally not needed; doesn't work well on build agents
        buildStepsToSkip += BuildOptions.WINDOWS_EXE_INSTALLER_STEP
      }
    }

    runBlocking(Dispatchers.Default) {
      val context = createCommunityBuildContext(options)
      CompilationTasks.create(context).compileModules(moduleNames = null, includingTestsInModules = listOf("intellij.platform.jps.build.tests"))
      buildDistributions(context)
      spanBuilder("build standalone JPS").use {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context)
      }
    }
  }
}
