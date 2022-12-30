// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.addToClasspathAgent.AddToClasspathUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
internal class KotlinBinaries(private val communityHome: BuildDependenciesCommunityRoot, private val messages: BuildMessages) {
  val kotlinCompilerHome: Path by lazy {
    val compilerHome = KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(communityHome)
    val kotlinc = compilerHome.resolve("bin/kotlinc")
    check(Files.exists(kotlinc)) { "Kotlin compiler home is missing under the path: $compilerHome" }
    compilerHome
  }

  private val kotlinJpsPlugin: Path by lazy {
    val jpsPlugin = KotlinCompilerDependencyDownloader.downloadKotlinJpsPlugin(communityHome)
    jpsPlugin
  }

  suspend fun loadKotlinJpsPluginToClassPath() {
    val required = KotlinCompilerDependencyDownloader.getKotlinJpsPluginVersion(communityHome)

    val current = getCurrentKotlinJpsPluginVersionFromClassPath()
    if (current != null) {
      check(current == required) {
        "Currently loaded Kotlin JPS plugin version is '$current', but required '$required'"
      }

      // already loaded
      return
    }

    messages.info("Loading Kotlin JPS plugin from $kotlinJpsPlugin")
    AddToClasspathUtil.addToClassPathViaAgent(listOf(kotlinJpsPlugin))

    val afterLoad = getCurrentKotlinJpsPluginVersionFromClassPath()
    check(afterLoad == required) {
      "Loaded Kotlin JPS plugin version '$afterLoad', but required '$required'"
    }
  }

  private fun getCurrentKotlinJpsPluginVersionFromClassPath(): String? {
    val stream = javaClass.classLoader.getResourceAsStream("META-INF/compiler.version") ?: return null
    return stream.use { stream.readAllBytes().decodeToString() }
  }
}