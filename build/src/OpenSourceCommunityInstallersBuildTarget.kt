// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.buildCommunityStandaloneJpsBuilder
import org.jetbrains.intellij.build.createCommunityBuildContext
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.runBlockingOnVirtualThreads
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use

@ApiStatus.Internal
object OpenSourceCommunityInstallersBuildTarget {
  /**
   * The steps that are excessive because the results are never published by `.github/workflows/IntelliJ_IDEA.yml`.
   * Also, skipping them allows sparing the GitHub runner's disk space.
   */
  private val BUILD_STEPS_DISABLED_FOR_GITHUB_ACTIONS: Set<String> = setOf(
    BuildOptions.WINDOWS_ZIP_STEP,
    BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP,
    BuildOptions.SOURCES_ARCHIVE_STEP,
    BuildOptions.ARCHIVE_PLUGINS,
  )

  val OPTIONS: BuildOptions = BuildOptions().apply {
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

  @JvmStatic
  fun main(args: Array<String>) {
    runBlockingOnVirtualThreads {
      val options = OPTIONS.copy(buildStepsToSkip = OPTIONS.buildStepsToSkip + BUILD_STEPS_DISABLED_FOR_GITHUB_ACTIONS)
      val context = createCommunityBuildContext(options)
      context.compileModules(moduleNames = null, includingTestsInModules = listOf("intellij.platform.jps.build.tests"))
      buildDistributions(context)
      spanBuilder("build standalone JPS").use {
        buildCommunityStandaloneJpsBuilder(targetDir = context.paths.artifactDir.resolve("jps"), context)
      }
    }
  }
}
