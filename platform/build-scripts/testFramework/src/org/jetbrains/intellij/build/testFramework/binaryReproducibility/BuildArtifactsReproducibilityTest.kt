// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.testFramework.binaryReproducibility

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.getOsDistributionBuilder
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

class BuildArtifactsReproducibilityTest {
  private val buildDateInSeconds = System.getenv("SOURCE_DATE_EPOCH")?.toLongOrNull()
  private val randomSeedNumber = Random().nextLong()
  private lateinit var diffDirectory: Path

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
    // FIXME IJI-823 workaround
    options.buildStepsToSkip.add(BuildOptions.PREBUILD_SHARED_INDEXES)
    options.buildStepsToSkip.remove(BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP)
    options.buildMacArtifactsWithRuntime = true
  }

  fun compare(context1: BuildContext, context2: BuildContext) {
    assert(isEnabled)
    assert(!this::diffDirectory.isInitialized)
    diffDirectory = System.getProperty("intellij.build.test.artifacts.reproducibility.diffDir")?.let { Path.of(it) }
                    ?: context1.paths.artifactDir.resolve(".diff")
    val errors = OsFamily.ALL.asSequence().flatMap { os ->
      val artifacts1 = getOsDistributionBuilder(os, context = context1)?.getArtifactNames(context1)
      val artifacts2 = getOsDistributionBuilder(os, context = context2)?.getArtifactNames(context2)
      assert(artifacts1 == artifacts2)
      if (artifacts1 != null) {
        artifacts1.map { "artifacts/$it" } +
        "dist.${os.distSuffix}" +
        JvmArchitecture.ALL.map { "dist.${os.distSuffix}.$it" }
      }
      else emptyList()
    }.plus("dist.all").plus("dist").mapNotNull {
      val path1 = context1.paths.buildOutputDir.resolve(it)
      val path2 = context2.paths.buildOutputDir.resolve(it)
      if (!path1.exists() && !path2.exists()) {
        context1.messages.warning("Neither $path1 nor $path2 exists")
        return@mapNotNull null
      }
      val diff = diffDirectory.resolve(it)
      val test = FileTreeContentTest(diff, context1.paths.tempDir)
      val error = when {
        path1.isDirectory() && path2.isDirectory() -> test.assertTheSameDirectoryContent(path1, path2)
        path1.isRegularFile() && path2.isRegularFile() -> test.assertTheSameFile(Path.of(it),
                                                                                 context1.paths.buildOutputDir,
                                                                                 context2.paths.buildOutputDir)
        else -> error("Unable to compare $path1 and $path2")
      }
      if (error != null) {
        context1.messages.artifactBuilt("$diff")
      }
      else {
        context1.messages.info("$path1 and $path2 are byte-to-byte identical.")
      }
      error
    }.toList()
    if (errors.isNotEmpty()) {
      throw Exception("Build is not reproducible").apply {
        errors.forEach(::addSuppressed)
      }
    }
  }
}