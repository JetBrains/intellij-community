// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import org.jetbrains.intellij.build.BuildLifetime
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.StandaloneMavenArtifacts
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name

/**
 * Builds the IntelliJ Standalone Analysis artifacts into `<artifacts>/maven-artifacts`, see [StandaloneMavenArtifacts].
 * The output holds the POM of each layer and the artifact of every module the POMs reference, so it works as a local Maven repository.
 * Run it with `bazel run //build:standalone_maven_artifacts` from the `community` directory.
 */
@OptIn(ExperimentalPathApi::class)
internal object StandaloneMavenArtifactsBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    BuildLifetime().use { lifetime ->
      val communityRoot = COMMUNITY_ROOT.communityRoot
      val context = createBuildContext(
        projectHome = communityRoot,
        productProperties = IdeaCommunityProperties(communityRoot), lifetime = lifetime,
      )

      context.compileModules(StandaloneMavenArtifacts.LAYERS)

      val builder = MavenArtifactsBuilder(context)
      val outputDir = context.paths.artifactDir.resolve("maven-artifacts")
      for (layer in StandaloneMavenArtifacts.LAYERS) {
        outputDir.resolve(builder.generateMavenCoordinates(layer, context.buildNumber).directoryPath).deleteRecursively()
      }

      val artifacts = builder.generateMavenArtifacts(moduleNamesToPublish = StandaloneMavenArtifacts.LAYERS, outputDir = outputDir.name)
      StandaloneMavenArtifacts.validate(artifacts)
      context.notifyArtifactBuilt(outputDir)
    }
  }
}
