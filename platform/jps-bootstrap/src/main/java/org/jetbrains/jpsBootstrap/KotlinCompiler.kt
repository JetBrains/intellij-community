// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.downloadFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.extractFileToCacheLocation
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.getUriForMavenArtifact
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.info
import org.jetbrains.intellij.build.dependencies.BuildDependenciesLogging.verbose
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.toPath

object KotlinCompiler {
  private const val INTELLIJ_DEPENDENCIES_REPOSITORY_URL =
    "https://cache-redirector.jetbrains.com/intellij-dependencies"

  private val MAVEN_LOCAL_URL by lazy { "file://${System.getProperty("user.home")}/.m2/repository" }

  private const val USE_MAVEN_LOCAL_PROPERTY = "kotlin.jps.use.maven.local"
  private fun shouldUseMavenLocal(): Boolean {
    return System.getProperty(USE_MAVEN_LOCAL_PROPERTY) == "true"
  }

  private fun getMavenRepositoryUrl(): String = if (shouldUseMavenLocal()) {
    MAVEN_LOCAL_URL
  } else {
    INTELLIJ_DEPENDENCIES_REPOSITORY_URL
  }


  fun downloadAndExtractKotlinCompiler(communityRoot: BuildDependenciesCommunityRoot): Path {
    // We already have kotlin JPS in the classpath, fetch version from it
    val kotlincVersion = javaClass.classLoader.getResourceAsStream("META-INF/compiler.version")
      .use { inputStream -> inputStream!!.readAllBytes().decodeToString() }
    info("Kotlin compiler version is $kotlincVersion")

    val kotlincUrl = getUriForMavenArtifact(
      getMavenRepositoryUrl(),
      "org.jetbrains.kotlin",
      "kotlin-dist-for-ide",
      kotlincVersion,
      "jar")
    val kotlincDist = if (shouldUseMavenLocal()) {
      val path = kotlincUrl.toPath()
      check(path.exists()) { "kotlin-dist-for-ide was not found in the local Maven repository" }
      path
    } else {
      downloadFileToCacheLocation(communityRoot, kotlincUrl)
    }
    val kotlinc = extractFileToCacheLocation(communityRoot, kotlincDist)
    verbose("Kotlin compiler is at $kotlinc")
    return kotlinc
  }
}
