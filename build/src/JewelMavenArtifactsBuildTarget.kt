// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.IdeaCommunityProperties
import org.jetbrains.intellij.build.JewelMavenArtifacts
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.maven.MavenArtifactsBuilder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.name

@OptIn(ExperimentalPathApi::class)
internal object JewelMavenArtifactsBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val context = BuildContextImpl.createContext(
        projectHome = ULTIMATE_HOME,
        productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
      )
      CompilationTasks.create(context).compileModules(JewelMavenArtifacts.ALL_MODULES)
      val builder = MavenArtifactsBuilder(context)
      val outputDir = context.paths.artifactDir.resolve("maven-artifacts")
      outputDir.deleteRecursively()
      builder.generateMavenArtifacts(
        JewelMavenArtifacts.ALL_MODULES,
        outputDir = outputDir.name,
        validate = true,
      )
      context.notifyArtifactBuilt(outputDir)
    }
  }
}