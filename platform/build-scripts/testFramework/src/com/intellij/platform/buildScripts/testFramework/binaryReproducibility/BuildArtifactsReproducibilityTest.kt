// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.testFramework.binaryReproducibility

import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.channels.Channel
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.reproducibleBuilds.diffTool.FileTreeContentComparison
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.writeText

internal class BuildArtifactsReproducibilityTest {
  private val randomSeedNumber = Random().nextLong()
  private val iterationChannel = Channel<BuildContext>()
  val iterations: Int = if (isEnabled) 2 else 1

  companion object {
    val isEnabled: Boolean = System.getProperty("intellij.build.test.artifacts.reproducibility") == "true"
  }

  fun configure(options: BuildOptions) {
    if (!isEnabled) {
      return
    }

    options.randomSeedNumber = randomSeedNumber
    options.buildStepsToSkip.remove(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)
    options.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES) // FIXME IDEA-311987 IDEA-310505
    options.buildUnixSnaps = true
  }

  suspend fun iterationFinished(iterationNumber: Int, build: BuildContext) {
    if (!isEnabled) return
    build.cleanBuildOutput()
    if (iterationNumber == 1) {
      iterationChannel.send(build)
      /**
       * Waiting for [compare] to complete not to clean up [BuildPaths.buildOutputDir] of a [build]
       */
      iterationChannel.receive()
    }
    else {
      val otherBuild = iterationChannel.receive()
      compare(build, otherBuild)
      iterationChannel.send(build)
    }
  }

  private fun compare(build1: BuildContext, build2: BuildContext) {
    assert(isEnabled)
    val buildId = System.getProperty("teamcity.build.id")
    val diffDirectory = when {
      /**
       * diff cannot be published if [org.jetbrains.intellij.build.BuildPaths.tempDir] is used
       * since it's cleaned up at the end of each test
       */
      buildId != null -> Files.createTempDirectory(build1.productProperties::class.java.simpleName +
                                                   this::class.java.simpleName +
                                                   buildId)
      else -> build1.paths.projectHome
    }.resolve(".diff")
    val test = FileTreeContentComparison(diffDirectory, build1.paths.tempDir)
    val result = test.assertTheSameDirectoryContent(
      build1.paths.artifactDir,
      build2.paths.artifactDir,
      deleteBothAfterwards = true
    )
    if (result.error != null) {
      build1.messages.artifactBuilt("$diffDirectory")
    }
    report(result, diffDirectory, build1)
  }

  private fun report(result: FileTreeContentComparison.ComparisonResult, reportDirectory: Path, context: BuildContext) {
    val report = context.applicationInfo.fullProductName
      .replace(" ", "-")
      .plus("-compared-files.txt")
      .let(reportDirectory::resolve)
    Files.createDirectories(report.parent)
    val reportText = result.comparedFiles
      .sortedBy { it.extension }
      .joinToString(separator = "\n")
    report.writeText(reportText)
    context.messages.artifactBuilt("$report")
    context.messages.info("Compared:\n$reportText")
    if (result.error != null) {
      throw Exception("Build is not reproducible").apply {
        addSuppressed(result.error)
      }
    }
    require(result.comparedFiles.isNotEmpty()) {
      "Nothing was compared"
    }
  }

  private fun BuildContext.cleanBuildOutput() {
    Files.newDirectoryStream(paths.buildOutputDir).use { content ->
      content.filter {
        it != paths.artifactDir && it != paths.logDir
      }.forEach(NioFiles::deleteRecursively)
    }
    Files.newDirectoryStream(paths.artifactDir).use { content ->
      content.filter {
        it.name == "unscrambled" || it.name == "scramble-logs"
      }.forEach(NioFiles::deleteRecursively)
    }
  }
}
