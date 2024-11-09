// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
class ArchivedCompilationOutputStorage(
  private val paths: BuildPaths,
  private val classesOutputDirectory: Path,
  val archivedOutputDirectory: Path = getArchiveStorage(classesOutputDirectory.parent),
) {
  private val unarchivedToArchivedMap = ConcurrentHashMap<Path, Path>()
  private var archiveIfAbsent = true

  internal fun loadMetadataFile(metadataFile: Path) {
    val metadata = Json.decodeFromString<CompilationPartsMetadata>(Files.readString(metadataFile))
    for (entry in metadata.files) {
      unarchivedToArchivedMap.put(classesOutputDirectory.resolve(entry.key), archivedOutputDirectory.resolve(entry.key).resolve("${entry.value}.jar"))
    }
    archiveIfAbsent = false
  }

  suspend fun getArchived(path: Path): Path {
    if (Files.isRegularFile(path) || !path.startsWith(classesOutputDirectory)) {
      return path
    }

    unarchivedToArchivedMap.get(path)?.let {
      return it
    }

    if (!archiveIfAbsent) {
      return path
    }

    val archived = archive(path)
    return unarchivedToArchivedMap.putIfAbsent(path, archived) ?: archived
  }

  private suspend fun archive(path: Path): Path {
    val name = classesOutputDirectory.relativize(path).toString()

    val archive = Files.createTempFile(paths.tempDir, name.replace(File.separator, "_"), ".jar")
    Files.deleteIfExists(archive)
    val hash = packAndComputeHash(addDirEntriesMode = AddDirEntriesMode.ALL, name = name, archive = archive, directory = path)

    val result = archivedOutputDirectory.resolve(name).resolve("$hash.jar")
    Files.createDirectories(result.parent)
    Files.move(archive, result, StandardCopyOption.REPLACE_EXISTING)

    return result
  }

  internal fun getMapping(): List<Map.Entry<Path, Path>> = unarchivedToArchivedMap.entries.sortedBy { it.key.invariantSeparatorsPathString }
}