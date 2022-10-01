// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.CompilationContextImpl
import org.jetbrains.intellij.build.impl.addToClasspathAgent.AddToClasspathUtil
import java.nio.file.Files
import java.nio.file.Path

private suspend fun getKotlinCompilerHome(communityHome: BuildDependenciesCommunityRoot): Path {
  val compilerHome = KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(communityHome)
  val kotlinc = compilerHome.resolve("bin/kotlinc")
  check(Files.exists(kotlinc)) { "Kotlin compiler home is missing under the path: $compilerHome" }
  return compilerHome
}

/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
suspend fun loadKotlinJpsPluginToClassPath(communityHome: BuildDependenciesCommunityRoot): Path {
  val required = KotlinCompilerDependencyDownloader.getKotlinJpsPluginVersion(communityHome)

  val current = getCurrentKotlinJpsPluginVersionFromClassPath()
  if (current != null) {
    check(current == required) {
      "Currently loaded Kotlin JPS plugin version is '$current', but required '$required'"
    }

    // already loaded
    return getKotlinCompilerHome(communityHome)
  }

  val kotlinJpsPlugin = KotlinCompilerDependencyDownloader.downloadKotlinJpsPlugin(communityHome)
  Span.current().addEvent("Loading Kotlin JPS plugin from $kotlinJpsPlugin")
  AddToClasspathUtil.addToClassPathViaAgent(listOf(kotlinJpsPlugin))

  val afterLoad = getCurrentKotlinJpsPluginVersionFromClassPath()
  check(afterLoad == required) {
    "Loaded Kotlin JPS plugin version '$afterLoad', but required '$required'"
  }

  return getKotlinCompilerHome(communityHome)
}

private fun getCurrentKotlinJpsPluginVersionFromClassPath(): String? {
  val stream = CompilationContextImpl::class.java.classLoader.getResourceAsStream("META-INF/compiler.version") ?: return null
  return stream.use { stream.readAllBytes().decodeToString() }
}