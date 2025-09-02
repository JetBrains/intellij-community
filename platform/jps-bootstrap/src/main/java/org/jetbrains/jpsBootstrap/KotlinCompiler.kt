// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.downloadFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getUriForMavenArtifact
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.verbose
import java.nio.file.Path

object KotlinCompiler {
  private const val KOTLIN_IDE_MAVEN_REPOSITORY_URL =
    "https://cache-redirector.jetbrains.com/intellij-dependencies"

  fun downloadAndExtractKotlinCompiler(communityRoot: BuildDependenciesCommunityRoot): Path {
    // We already have kotlin JPS in the classpath, fetch version from it
    val kotlincVersion = javaClass.classLoader.getResourceAsStream("META-INF/compiler.version")
      .use { inputStream -> inputStream!!.readAllBytes().decodeToString() }
    info("Kotlin compiler version is $kotlincVersion")

    val kotlincUrl = getUriForMavenArtifact(
      KOTLIN_IDE_MAVEN_REPOSITORY_URL,
      "org.jetbrains.kotlin",
      "kotlin-dist-for-ide",
      kotlincVersion,
      "jar")
    val kotlincDist = downloadFileToCacheLocation(communityRoot, kotlincUrl)
    val kotlinc = extractFileToCacheLocation(communityRoot, kotlincDist)
    verbose("Kotlin compiler is at $kotlinc")
    return kotlinc
  }
}
