// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.io.AddDirEntriesMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.invariantSeparatorsPathString

internal class ArchivedCompilationOutputStorage(
  private val paths: BuildPaths,
  private val classesOutputDirectory: Path,
  @JvmField internal val archivedOutputDirectory: Path,
  initialMapping: Map<Path, Path>,
  private var archiveIfAbsent: Boolean = initialMapping.isEmpty(),
) {
  private val unarchivedToArchivedMap = ConcurrentHashMap(initialMapping)

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

internal fun createArchivedStorage(delegate: CompilationContext): ArchivedCompilationOutputStorage {
  val classesOutputDirectory = delegate.classesOutputDirectory
  val archivedOutputDirectory = getArchiveStorage(classesOutputDirectory.parent)

  val mapping = delegate.options.pathToCompiledClassesArchivesMetadata?.let { paths ->
    delegate.messages.info("Loading archived compilation mappings from metadata file: $paths")
    val metadata = Files.newInputStream(paths).use { Json.decodeFromStream<CompilationPartsMetadata>(it) }
    metadata.files.entries.associateTo(HashMap(metadata.files.size)) { (key, value) ->
      classesOutputDirectory.resolve(key) to archivedOutputDirectory.resolve(key).resolve("$value.jar")
    }
  } ?: System.getProperty("intellij.test.jars.mapping.file")?.let {
    delegate.messages.info("Loading archived compilation mappings from mapping file: $it")
    Files.readAllLines(Path.of(it)).asSequence().mapNotNull { line ->
      val eq = line.indexOf('=')
      if (eq == -1) null
      else classesOutputDirectory.resolve(line.substring(0, eq)) to Path.of(line.substring(eq + 1))
    }.toMap(HashMap())
  } ?: emptyMap()

  return ArchivedCompilationOutputStorage(
    paths = delegate.paths,
    classesOutputDirectory = classesOutputDirectory,
    archivedOutputDirectory = archivedOutputDirectory,
    initialMapping = mapping,
  )
}

