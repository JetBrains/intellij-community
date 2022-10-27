// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework.binaryReproducibility

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.reproducibleBuilds.diffTool.FileTreeContentComparison
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.writeText

class BuildArtifactsReproducibilityTest {
  private val buildDateInSeconds = System.getenv("SOURCE_DATE_EPOCH")?.toLongOrNull()
  private val randomSeedNumber = Random().nextLong()

  companion object {
    val isEnabled = System.getProperty("intellij.build.test.artifacts.reproducibility") == "true"
  }

  fun configure(options: BuildOptions) {
    assert(isEnabled)
    requireNotNull(buildDateInSeconds) {
      "SOURCE_DATE_EPOCH environment variable is required"
    }
    options.buildDateInSeconds = buildDateInSeconds
    options.randomSeedNumber = randomSeedNumber
    options.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES) // FIXME IJI-823 workaround
    options.buildStepsToSkip.remove(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)
    options.buildMacArtifactsWithRuntime = true
    options.buildUnixSnaps = true
  }

  fun compare(build1: BuildContext, build2: BuildContext) {
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
    val result = test.assertTheSameDirectoryContent(build1.paths.artifactDir, build2.paths.artifactDir)
    if (result.error != null) {
      build1.messages.artifactBuilt("$diffDirectory")
    }
    report(result, diffDirectory, build1)
  }

  private fun report(result: FileTreeContentComparison.ComparisonResult, diffDirectory: Path, context: BuildContext) {
    val report = context.applicationInfo.productName
      .replace(" ", "-")
      .plus("-compared-files.txt")
      .let(diffDirectory::resolve)
    Files.createDirectories(report.parent)
    val reportText = result.comparedFiles.joinToString(separator = "\n")
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
}