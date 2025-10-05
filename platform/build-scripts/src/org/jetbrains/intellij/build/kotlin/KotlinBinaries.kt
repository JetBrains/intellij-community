// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin

import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.addToClasspathAgent.AddToClasspathUtil
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
@ApiStatus.Internal
class KotlinBinaries(private val communityHome: BuildDependenciesCommunityRoot) {
  companion object {
    val kotlinCompilerExecutables: List<String>
      get() {
        return persistentListOf(
          "plugins/Kotlin/kotlinc/bin/kotlin",
          "plugins/Kotlin/kotlinc/bin/kotlinc",
          "plugins/Kotlin/kotlinc/bin/kotlinc-js",
          "plugins/Kotlin/kotlinc/bin/kotlinc-jvm"
        )
      }
  }

  val kotlinCompilerHome: Path by lazy {
    val compilerHome = KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(communityHome)
    val kotlinc = compilerHome.resolve("bin/kotlinc")
    check(Files.exists(kotlinc)) { "Kotlin compiler home is missing under the path: $compilerHome" }
    compilerHome
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

    val jpsPlugin = KotlinCompilerDependencyDownloader.downloadKotlinJpsPlugin(communityHome)
    Span.current().addEvent("load Kotlin JPS plugin from $jpsPlugin")
    AddToClasspathUtil.addToClassPathViaAgent(listOf(jpsPlugin))

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