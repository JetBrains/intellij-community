// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.intellij.build.io.ZipFileWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.*

internal fun getIncludedModules(entries: Sequence<DistributionFileEntry>): Sequence<String> {
  return entries.mapNotNull { (it as? ModuleOutputEntry)?.moduleName }.distinct()
}

internal fun buildJarContentReport(contentReport: ContentReport, zipFileWriter: ZipFileWriter, buildPaths: BuildPaths, context: BuildContext) {
  zipFileWriter.uncompressedData("platform.yaml", buildPlatformContentReport(contentReport, buildPaths, context.getDistFiles(os = null, arch = null)))
  zipFileWriter.uncompressedData("bundled-plugins.yaml", buildPluginContentReport(contentReport.bundledPlugins, buildPaths))
  zipFileWriter.uncompressedData("non-bundled-plugins.yaml", buildPluginContentReport(contentReport.nonBundledPlugins, buildPaths))
}

private fun buildPluginContentReport(pluginToEntries: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, buildPaths: BuildPaths): ByteArray {
  val out = ByteArrayOutputStream()
  val writer = createYamlGenerator(out)

  writer.writeStartArray()
  for ((plugin, entries) in pluginToEntries) {
    val fileToPresentablePath = HashMap<Path, String>()

    val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
    for (entry in entries) {
      val presentablePath = fileToPresentablePath.computeIfAbsent(entry.path) {
        if (entry.path.startsWith(plugin.dir)) {
          plugin.dir.relativize(entry.path).toString().replace(File.separatorChar, '/')
        }
        else {
          shortenAndNormalizePath(it, buildPaths)
        }
      }
      fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
    }

    writer.writeStartObject()
    writer.writeStringField("mainModule", plugin.layout.mainModule)
    if (plugin.os != null) {
      writer.writeStringField("os", plugin.os.osId)
    }

    val contentModuleReason = "<- ${plugin.layout.mainModule} (plugin content)"

    writer.writeArrayFieldStart("content")
    for ((filePath, fileEntries) in fileToEntry) {
      writer.writeStartObject()
      writer.writeStringField("name", filePath)
      writeProjectLibs(entries = fileEntries, writer = writer, buildPaths = buildPaths, isInner = false)

      if (fileEntries.all { it is ModuleLibraryFileEntry }) {
        writeSeparatePackedModuleLibrary(fileEntries = fileEntries, writer = writer, buildPaths = buildPaths)
        writer.writeEndObject()
        continue
      }

      writeModules(
        writer = writer,
        fileEntries = fileEntries,
        buildPaths = buildPaths,
        reasonFilter = { it.reason != contentModuleReason },
      )
      writeModules(
        writer = writer,
        fileEntries = fileEntries,
        reasonFilter = { it.reason == contentModuleReason },
        buildPaths = buildPaths,
        fieldName = "contentModules",
        writeReason = false,
      )

      writer.writeEndObject()
    }
    writer.writeEndArray()

    writer.writeEndObject()
  }
  writer.writeEndArray()

  writer.close()
  return out.toByteArray()
}

private fun createYamlGenerator(out: ByteArrayOutputStream): YAMLGenerator {
  return YAMLFactory().createGenerator(out)
    .useDefaultPrettyPrinter()
    .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
}

private fun buildPlatformContentReport(contentReport: ContentReport, buildPaths: BuildPaths, distFiles: Collection<DistFile>): ByteArray {
  val out = ByteArrayOutputStream()
  val writer = createYamlGenerator(out)
  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()
  for (entry in contentReport.platform) {
    val presentablePath = fileToPresentablePath.computeIfAbsent(entry.path) {
      shortenAndNormalizePath(it, buildPaths)
    }
    fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
  }
  writer.writeStartArray()
  for ((filePath, fileEntries) in fileToEntry) {
    writer.writeStartObject()
    writer.writeStringField("name", filePath)
    writeProjectLibs(entries = fileEntries, writer = writer, buildPaths = buildPaths, isInner = true)
    writeModules(writer = writer, fileEntries = fileEntries, buildPaths = buildPaths)
    writer.writeEndObject()
  }

  for (item in distFiles) {
    writer.writeStartObject()

    writer.writeStringField("name", item.relativePath)
    item.os?.let { writer.writeStringField("os", it.osId) }
    item.arch?.let { writer.writeStringField("arch", it.dirName) }

    writer.writeEndObject()
  }

  writer.writeStartObject()

  writer.writeObjectField("name", "plugins")
  writer.writeArrayFieldStart("bundled")
  for (p in contentReport.bundledPlugins) {
    writer.writeString(p.first.layout.mainModule)
  }
  writer.writeEndArray()
  writer.writeArrayFieldStart("nonBundled")
  for (p in contentReport.nonBundledPlugins) {
    writer.writeString(p.first.layout.mainModule)
  }
  writer.writeEndArray()

  writer.writeEndObject()

  writer.writeEndArray()
  writer.close()
  return out.toByteArray()
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

private fun shortenAndNormalizePath(file: Path, buildPaths: BuildPaths, extraRoot: Path? = null): String {
  val result = shortenPath(file, buildPaths, extraRoot).replace(File.separatorChar, '/')
  return if (result.startsWith("temp/")) result.substring("temp/".length) else result
}

private fun writeModules(
  writer: JsonGenerator,
  fileEntries: List<DistributionFileEntry>,
  buildPaths: BuildPaths,
  fieldName: String = "modules",
  writeReason: Boolean = true,
  reasonFilter: (ModuleOutputEntry) -> Boolean = { true },
) {
  var opened = false
  for (entry in fileEntries) {
    if (entry !is ModuleOutputEntry || !reasonFilter(entry)) {
      continue
    }

    if (!opened) {
      writer.writeArrayFieldStart(fieldName)
      opened = true
    }

    writer.writeStartObject()
    val moduleName = entry.moduleName
    writeModuleItem(writer, entry, writeReason = writeReason)
    writeModuleLibraries(fileEntries = fileEntries, moduleName = moduleName, writer = writer, buildPaths = buildPaths)
    writer.writeEndObject()
  }
  if (opened) {
    writer.writeEndArray()
  }
}

private fun writeModuleItem(writer: JsonGenerator, entry: ModuleOutputEntry, writeReason: Boolean) {
  writer.writeStringField("name", entry.moduleName)
  writer.writeNumberField("size", entry.size)
  if (writeReason) {
    entry.reason?.let {
      writer.writeStringField("reason", it)
    }
  }
}

private fun writeModuleLibraries(fileEntries: List<DistributionFileEntry>, moduleName: String, writer: JsonGenerator, buildPaths: BuildPaths) {
  val entriesGroupedByLibraryName = LinkedHashMap<String, MutableList<ModuleLibraryFileEntry>>()
  for (entry in fileEntries) {
    if (entry is ModuleLibraryFileEntry) {
      if (entry.moduleName == moduleName) {
        entriesGroupedByLibraryName.computeIfAbsent(entry.libraryName) { ArrayList() }.add(entry)
      }
    }
  }

  if (entriesGroupedByLibraryName.isEmpty()) {
    return
  }

  writer.writeObjectFieldStart("libraries")
  for ((libName, entries) in entriesGroupedByLibraryName) {
    writeFiles(writer = writer, entries = entries, buildPaths = buildPaths, arrayFieldName = libName)
  }
  writer.writeEndObject()
}

private fun writeSeparatePackedModuleLibrary(fileEntries: List<DistributionFileEntry>, writer: JsonGenerator, buildPaths: BuildPaths) {
  val entriesGroupedByLibraryName = LinkedHashMap<String, MutableList<ModuleLibraryFileEntry>>()
  for (entry in fileEntries) {
    if (entry is ModuleLibraryFileEntry) {
      entriesGroupedByLibraryName.computeIfAbsent(entry.libraryName) { ArrayList() }.add(entry)
    }
  }

  require(entriesGroupedByLibraryName.size == 1) {
    "Expected only one library, but got: $entriesGroupedByLibraryName"
  }

  val (libName, entries) = entriesGroupedByLibraryName.iterator().next()
  writer.writeObjectField("library", libName)
  writer.writeObjectField("module", entries.first().moduleName)
  writeFiles(writer = writer, entries = entries, buildPaths = buildPaths)
}

private fun writeFiles(
  writer: JsonGenerator,
  entries: List<LibraryFileEntry>,
  buildPaths: BuildPaths,
  arrayFieldName: String = "files",
) {
  writer.writeArrayFieldStart(arrayFieldName)
  for (entry in entries) {
    writer.writeStartObject()
    writer.writeStringField("name", shortenAndNormalizePath(entry.libraryFile!!, buildPaths))
    writer.writeNumberField("size", entry.size)
    writer.writeEndObject()
  }
  writer.writeEndArray()
}

private fun writeProjectLibs(entries: List<DistributionFileEntry>, writer: JsonGenerator, buildPaths: BuildPaths, isInner: Boolean) {
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

  if (!isInner && map.size == 1) {
    val (libraryData, entries) = map.iterator().next()
    writer.writeObjectField("library", libraryData.libraryName)
    writeFiles(writer = writer, entries = entries, buildPaths = buildPaths)
    writer.writeObjectField("reason", libraryData.reason)
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
