// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeowners.monorepo.resolver

import com.intellij.codeowners.CodeOwners
import com.intellij.codeowners.model.OwnershipMatch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo
import kotlin.io.path.useLines

/**
 * Resolves code owners for test classes by combining file-location and module-path NDJSON artifacts
 * with the [CodeOwners] index.
 *
 * Resolution algorithm:
 * 1. Look up (module, package, file) in file-locations map to get the relative path inside the module
 * 2. Resolve the full source file path using the module path
 * 3. Query [CodeOwners.getOwner] with the repository-relative path
 *
 * @see CodeOwners
 */
class TestOwnerResolver(
  private val codeOwners: CodeOwners,
  private val modulePathMap: Map<String, Path>,
  private val fileLocationMap: Map<FileLocationKey, String>,
  private val repositoryRoot: Path,
) {
  data class FileLocationKey(val moduleName: String, val packagePath: String, val fileName: String)

  fun getOwner(moduleName: String, packagePath: String, fileName: String): OwnershipMatch? {
    val key = FileLocationKey(moduleName, packagePath, fileName)
    val relativePath = fileLocationMap[key] ?: return null
    val modulePath = modulePathMap[moduleName] ?: return null
    val testSourceFile = modulePath
      .resolve(relativePath)
      .resolve(fileName)
      .relativeTo(repositoryRoot)
      .invariantSeparatorsPathString
    return codeOwners.getOwner(testSourceFile)
  }

  companion object {
    private val logger: Logger = Logger.getLogger(TestOwnerResolver::class.java.name)
    private val json = Json { ignoreUnknownKeys = true }

    fun create(repositoryRoot: Path, fileLocations: Path, modulePaths: Path): TestOwnerResolver? {
      if (!Files.exists(fileLocations) || !Files.exists(modulePaths)) return null
      return try {
        TestOwnerResolver(
          codeOwners = CodeOwners(repositoryRoot),
          modulePathMap = loadModulePaths(modulePaths, repositoryRoot),
          fileLocationMap = loadFileLocations(fileLocations),
          repositoryRoot = repositoryRoot,
        )
      }
      catch (e: Exception) {
        logger.warning("Failed to create TestOwnerResolver: ${e.message}")
        null
      }
    }

    private fun loadModulePaths(file: Path, repositoryRoot: Path): Map<String, Path> {
      val map = mutableMapOf<String, Path>()
      file.useLines { lines ->
        for (line in lines) {
          if (line.isBlank()) continue
          try {
            val entry = json.decodeFromString<ModulePathEntry>(line)
            map[entry.moduleName] = repositoryRoot.resolve(entry.modulePath)
          }
          catch (e: Exception) {
            logger.warning("Failed to parse module path entry: $line - ${e.message}")
          }
        }
      }
      return map
    }

    private fun loadFileLocations(file: Path): Map<FileLocationKey, String> {
      val map = mutableMapOf<FileLocationKey, String>()
      file.useLines { lines ->
        for (line in lines) {
          if (line.isBlank()) continue
          try {
            val entry = json.decodeFromString<FileLocationEntry>(line)
            val key = FileLocationKey(entry.moduleName, entry.packagePath, entry.fileName)
            map[key] = entry.relativePath
          }
          catch (e: Exception) {
            logger.warning("Failed to parse file location entry: $line - ${e.message}")
          }
        }
      }
      return map
    }
  }
}

@Serializable
data class FileLocationEntry(
  @SerialName("m") val moduleName: String,
  @SerialName("p") val packagePath: String,
  @SerialName("f") val fileName: String,
  @SerialName("rp") val relativePath: String,
)

@Serializable
data class ModulePathEntry(
  @SerialName("m") val moduleName: String,
  @SerialName("p") val modulePath: String,
)
