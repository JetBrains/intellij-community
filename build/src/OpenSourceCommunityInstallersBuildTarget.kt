// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use

@ApiStatus.Internal
object OpenSourceCommunityInstallersBuildTarget {
  val OPTIONS: BuildOptions = BuildOptions().apply {
    // do not bother external users about clean/incremental
    // just remove out/ directory for clean build
    incrementalCompilation = true
    useCompiledClassesFromProjectOutput = false
    buildStepsToSkip += BuildOptions.MAC_SIGN_STEP
    buildStepsToSkip += BuildOptions.WIN_SIGN_STEP
    // never published from .github/workflows/IntelliJ_IDEA.yml
    buildStepsToSkip += BuildOptions.WINDOWS_ZIP_STEP
    buildStepsToSkip += BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP
    if (OsFamily.currentOs == OsFamily.MACOS) {
      // generally not needed; doesn't work well on build agents
      buildStepsToSkip += BuildOptions.WINDOWS_EXE_INSTALLER_STEP
    }
  }

  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val context = createCommunityBuildContext(OPTIONS)
      CompilationTasks.create(context).compileModules(moduleNames = null, includingTestsInModules = listOf("intellij.platform.jps.build.tests"))
      buildDistributions(context)
      spanBuilder("build standalone JPS").use {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context)
      }
    }
  }
}
