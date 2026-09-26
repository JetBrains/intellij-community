// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.usages

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

internal class BazelTargetsInfo private constructor(
  private val projectRoot: Path,
  private val targets: TargetsFile,
) {
  companion object {
    private const val FILE_NAME = "bazel-targets.json"
    private const val CONF_PLACEHOLDER = "$" + "{CONF}"
    private val json = Json { ignoreUnknownKeys = true }

    fun fromProjectRoot(projectRoot: Path): BazelTargetsInfo {
      val file = projectRoot.resolve("build").resolve(FILE_NAME)
      return BazelTargetsInfo(projectRoot, loadTargets(file))
    }

    private fun loadTargets(file: Path): TargetsFile {
      if (!file.isRegularFile()) {
        return TargetsFile(emptyMap())
      }
      return json.decodeFromString(file.readText())
    }
  }

  fun getModuleJars(moduleName: String, withTests: Boolean): List<Path>? {
    val moduleInfo = targets.modules[moduleName] ?: return null
    return moduleInfo.productionJars.map(::resolveJarPath) +
           if (withTests) moduleInfo.testJars.map(::resolveJarPath) else emptyList()
  }

  fun getAllModules(): Set<String> = targets.modules.keys

  private fun resolveJarPath(path: String): Path {
    val resolvedPath = path.replace(CONF_PLACEHOLDER, currentBazelConfig())
    check('$' !in resolvedPath) { "Path still contains substitutions, which is not supported: $path" }
    return projectRoot.resolve(resolvedPath).normalize()
  }

  private fun currentBazelConfig(): String {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val bazelOsArch = when {
      osName.contains("linux") && arch in setOf("x86_64", "amd64") -> "k8"
      osName.contains("linux") && arch in setOf("aarch64", "arm64") -> "aarch64"
      osName.contains("win") && arch in setOf("x86_64", "amd64") -> "x64_windows"
      osName.contains("win") && arch in setOf("aarch64", "arm64") -> "arm64_windows"
      osName.contains("mac") && arch in setOf("aarch64", "arm64") -> "darwin_arm64"
      osName.contains("mac") && arch in setOf("x86_64", "amd64") -> "darwin_x86_64"
      else -> error("Unsupported OS/Arch: $osName $arch")
    }
    return "$bazelOsArch-fastbuild"
  }

  @Serializable
  data class TargetsFileModuleDescription(
    val productionTargets: List<String> = emptyList(),
    val productionJars: List<String> = emptyList(),
    val testTargets: List<String> = emptyList(),
    val testJars: List<String> = emptyList(),
  )

  @Serializable
  data class TargetsFile(
    val modules: Map<String, TargetsFileModuleDescription>,
  )
}
