// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.JewelMavenArtifacts
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name

@OptIn(ExperimentalPathApi::class)
internal object JewelMavenArtifactsBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) = runBlocking(Dispatchers.Default) {
    val communityRoot = COMMUNITY_ROOT.communityRoot
    val context = createBuildContext(
      projectHome = communityRoot,
      productProperties = IdeaCommunityProperties(communityRoot),
    )
    context.compileModules(JewelMavenArtifacts.ALL_MODULES)
    val builder = MavenArtifactsBuilder(context)
    val outputDir = context.paths.artifactDir.resolve("maven-artifacts")
    outputDir.deleteRecursively()
    builder.generateMavenArtifacts(moduleNamesToPublish = JewelMavenArtifacts.ALL_MODULES, outputDir = outputDir.name, validate = true)
    context.notifyArtifactBuilt(outputDir)
  }
}
