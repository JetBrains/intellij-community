// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal val Collection<DistributionFileEntry>.includedModules: Sequence<String>
  get() = asSequence().mapNotNull { (it as? ModuleOutputEntry)?.moduleName }.distinct()

/**
 * Provides mapping between files in the product distribution and modules and libraries in the project configuration. The generated JSON file
 * contains array of [DistributionFileEntry].
 */
internal fun buildJarContentReport(entries: Collection<DistributionFileEntry>, out: OutputStream?, buildPaths: BuildPaths) {
  val writer = JsonFactory().createGenerator(out).setPrettyPrinter(IntelliJDefaultPrettyPrinter())
  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()
  for (entry in entries) {
    val presentablePath = fileToPresentablePath.computeIfAbsent(entry.path) { shortenAndNormalizePath(it, buildPaths) }
    fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
  }
  writer.writeStartArray()
  for ((filePath, fileEntries) in fileToEntry) {
    writer.writeStartObject()
    writer.writeStringField("name", filePath)
    writeProjectLibs(fileEntries, writer, buildPaths)
    writeModules(writer = writer, fileEntries = fileEntries, buildPaths = buildPaths)
    writer.writeEndObject()
  }
  writer.writeEndArray()
  writer.close()
}

fun writeProjectStructureReport(entries: Collection<DistributionFileEntry>, file: Path, buildPaths: BuildPaths, extraRoot: Path? = null) {
  Files.createDirectories(file.parent)
  Files.newOutputStream(file).use { out ->
    val writer = JsonFactory().createGenerator(out).setPrettyPrinter(IntelliJDefaultPrettyPrinter())
    writer.use {
      writer.writeStartArray()
      for (entry in entries) {
        writer.writeStartObject()
        writer.writeStringField("path", shortenAndNormalizePath(entry.path, buildPaths, extraRoot))
        writer.writeStringField("type", entry.type)
        when (entry) {
          is ModuleLibraryFileEntry -> {
            writer.writeStringField("module", entry.moduleName)
            writer.writeStringField("libraryFile", shortenAndNormalizePath(entry.libraryFile!!, buildPaths, extraRoot))
            writer.writeNumberField("size", entry.size)
          }
          is ModuleOutputEntry -> {
            writer.writeStringField("module", entry.moduleName)
            writer.writeNumberField("size", entry.size)
          }
          is ModuleTestOutputEntry -> {
            writer.writeStringField("module", entry.moduleName)
          }
          is ProjectLibraryEntry -> {
            writer.writeStringField("library", entry.data.libraryName)
            writer.writeStringField("libraryFile", shortenAndNormalizePath(entry.libraryFile!!, buildPaths, extraRoot))
            writer.writeNumberField("size", entry.size)
          }
          else -> throw UnsupportedOperationException("${entry.type} is not supported")
        }
        writer.writeEndObject()
      }
      writer.writeEndArray()
    }
  }
}

private fun shortenPath(file: Path, buildPaths: BuildPaths, extraRoot: Path?): String {
  if (file.startsWith(MAVEN_REPO)) {
    return "\$MAVEN_REPOSITORY$/" + MAVEN_REPO.relativize(file).toString().replace(File.separatorChar, '/')
  }
  val projectHome = buildPaths.projectHome
  if (file.startsWith(projectHome)) {
    return "\$PROJECT_DIR$/" + projectHome.relativize(file).toString()
  }
  else {
    val buildOutputDir = buildPaths.buildOutputDir
    return when {
      file.startsWith(buildOutputDir) -> buildOutputDir.relativize(file).toString()
      extraRoot != null && file.startsWith(extraRoot) -> extraRoot.relativize(file).toString()
      else -> file.toString()
    }
  }
}

private val INDENTER = DefaultIndenter("  ", "\n")

private class IntelliJDefaultPrettyPrinter : DefaultPrettyPrinter() {
  override fun createInstance(): DefaultPrettyPrinter = IntelliJDefaultPrettyPrinter()

  init {
    _objectFieldValueSeparatorWithSpaces = ": "
    _objectIndenter = INDENTER
    _arrayIndenter = INDENTER
  }
}

private val MAVEN_REPO = Path.of(System.getProperty("user.home"), ".m2/repository")

private fun shortenAndNormalizePath(file: Path, buildPaths: BuildPaths, extraRoot: Path? = null): String {
  val result = shortenPath(file, buildPaths, extraRoot).replace(File.separatorChar, '/')
  return if (result.startsWith("temp/")) result.substring("temp/".length) else result
}

private fun writeModules(writer: JsonGenerator, fileEntries: List<DistributionFileEntry>, buildPaths: BuildPaths) {
  var opened = false
  for (entry in fileEntries) {
    if (entry !is ModuleOutputEntry) {
      continue
    }

    if (!opened) {
      writer.writeArrayFieldStart("modules")
      opened = true
    }

    writer.writeStartObject()
    val moduleName = entry.moduleName
    writer.writeStringField("name", moduleName)
    writer.writeNumberField("size", entry.size)
    writeModuleLibraries(fileEntries = fileEntries, moduleName = moduleName, writer = writer, buildPaths = buildPaths)
    writer.writeEndObject()
  }
  if (opened) {
    writer.writeEndArray()
  }
}

private fun writeModuleLibraries(fileEntries: List<DistributionFileEntry>,
                                 moduleName: String,
                                 writer: JsonGenerator,
                                 buildPaths: BuildPaths) {
  var opened = false
  for (entry in fileEntries) {
    if (entry !is ModuleLibraryFileEntry || entry.moduleName != moduleName) {
      continue
    }

    if (!opened) {
      writer.writeArrayFieldStart("libraries")
      opened = true
    }
    writer.writeStartObject()
    writer.writeStringField("name", shortenAndNormalizePath(entry.libraryFile!!, buildPaths))
    writer.writeNumberField("size", entry.size)
    writer.writeEndObject()
  }
  if (opened) {
    writer.writeEndArray()
  }
}

private fun writeProjectLibs(entries: List<DistributionFileEntry>, writer: JsonGenerator, buildPaths: BuildPaths) {
  // group by library
  val map = TreeMap<ProjectLibraryData, MutableList<ProjectLibraryEntry>> { o1, o2 ->
    o1.libraryName.compareTo(o2.libraryName)
  }
  for (entry in entries) {
    if (entry is ProjectLibraryEntry) {
      map.computeIfAbsent(entry.data) { mutableListOf() }.add(entry)
    }
  }

  if (map.isEmpty()) {
    return
  }

  writer.writeArrayFieldStart("projectLibraries")
  for ((data, value) in map) {
    writer.writeStartObject()
    writer.writeStringField("name", data.libraryName)
    writer.writeArrayFieldStart("files")
    for (fileEntry in value) {
      writer.writeStartObject()
      writer.writeStringField("name", shortenAndNormalizePath(fileEntry.libraryFile!!, buildPaths, null))
      writer.writeNumberField("size", fileEntry.size)
      writer.writeEndObject()
    }
    writer.writeEndArray()
    if (data.reason != null) {
      writer.writeStringField("reason", data.reason)
    }
    writeModuleDependents(writer, data)
    writer.writeEndObject()
  }
  writer.writeEndArray()
}

private fun writeModuleDependents(writer: JsonGenerator, data: ProjectLibraryData) {
  writer.writeObjectFieldStart("dependentModules")
  for ((key, value) in data.dependentModules) {
    writer.writeArrayFieldStart(key)
    for (moduleName in value.sorted()) {
      writer.writeString(moduleName)
    }
    writer.writeEndArray()
  }
  writer.writeEndObject()
}