// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

internal class ArchivedCompilationOutputStorage(
  private val paths: BuildPaths,
  private val classesOutputDirectory: Path,
  private val messages: BuildMessages,
  @JvmField internal val archivedOutputDirectory: Path = getArchiveStorage(classesOutputDirectory.parent),
) {
  private val unarchivedToArchivedMap = ConcurrentHashMap<Path, Path>()
  private var archiveIfAbsent = true

  internal fun loadMetadataFile(metadataFile: Path) {
    messages.info("Loading archived compilation mappings from metadata file: $metadataFile")
    val metadata = Files.newInputStream(metadataFile).use { Json.decodeFromStream<CompilationPartsMetadata>(it) }
    for (entry in metadata.files) {
      unarchivedToArchivedMap.put(classesOutputDirectory.resolve(entry.key), archivedOutputDirectory.resolve(entry.key).resolve("${entry.value}.jar"))
    }
    archiveIfAbsent = false
  }

  internal fun loadMapping(mappingFile: Path) {
    messages.info("Loading archived compilation mappings from mapping file: $mappingFile")
    for (line in Files.readAllLines(mappingFile)) {
      val eq = line.indexOf('=')
      if (eq == -1) continue
      unarchivedToArchivedMap.put(classesOutputDirectory.resolve(line.substring(0, eq)), Path.of(line.substring(eq + 1)))
    }
  }

   fun getArchived(path: Path): Path {
    if (Files.isRegularFile(path)) {
      return path
    }

    unarchivedToArchivedMap.get(path)?.let {
      return it
    }

    if (!archiveIfAbsent) {
      return path
    }

    if (Files.notExists(path)) {
      return path
    }

    val archived = archive(path)
    return unarchivedToArchivedMap.putIfAbsent(path, archived) ?: archived
  }

  private fun archive(path: Path): Path {
    if (!Files.newDirectoryStream(path).use { stream -> stream.iterator().hasNext() }) {
      // Empty dir, no need to archive
      return path
    }
    val name = classesOutputDirectory.relativize(path).toString()

    val archive = Files.createTempFile(paths.tempDir, name.replace(path.fileSystem.separator, "_"), ".jar")
    Files.deleteIfExists(archive)
    val hash = packAndComputeHash(addDirEntriesMode = AddDirEntriesMode.ALL, name = name, archive = archive, source = path)

    val result = archivedOutputDirectory.resolve(name).resolve("$hash.jar")
    Files.createDirectories(result.parent)
    Files.move(archive, result, StandardCopyOption.REPLACE_EXISTING)

    return result
  }

  internal fun getMapping(): List<Map.Entry<Path, Path>> = unarchivedToArchivedMap.entries.sortedBy { it.key.invariantSeparatorsPathString }
}
