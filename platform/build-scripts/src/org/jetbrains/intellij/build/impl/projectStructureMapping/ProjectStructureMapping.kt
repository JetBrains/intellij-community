// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment")

package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.apache.commons.io.output.ByteArrayOutputStream
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.intellij.build.io.ZipFileWriter
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

internal fun getIncludedModules(entries: Sequence<DistributionFileEntry>): Sequence<String> {
  return entries.mapNotNull { (it as? ModuleOutputEntry)?.moduleName }.distinct()
}

internal fun buildJarContentReport(contentReport: ContentReport, zipFileWriter: ZipFileWriter, buildPaths: BuildPaths, context: BuildContext) {
  zipFileWriter.uncompressedData("platform.yaml", ByteBuffer.wrap(buildPlatformContentReport(contentReport, buildPaths, context.getDistFiles(os = null, arch = null))), null)
  zipFileWriter.uncompressedData("bundled-plugins.yaml", ByteBuffer.wrap(buildPluginContentReport(contentReport.bundledPlugins, buildPaths)), null)
  zipFileWriter.uncompressedData("non-bundled-plugins.yaml", ByteBuffer.wrap(buildPluginContentReport(contentReport.nonBundledPlugins, buildPaths)), null)
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
      writeProjectLibs(fileEntries, writer, buildPaths)

      writeModules(
        writer = writer,
        fileEntries = fileEntries.asSequence().filter { it !is ModuleOutputEntry || it.reason != contentModuleReason },
        buildPaths = buildPaths
      )
      writeModules(
        writer = writer,
        fileEntries = fileEntries.asSequence().filter { it !is ModuleOutputEntry || it.reason == contentModuleReason },
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
    writeProjectLibs(fileEntries, writer, buildPaths)
    writeModules(writer = writer, fileEntries = fileEntries.asSequence(), buildPaths = buildPaths)
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

internal fun writeProjectStructureReport(contentReport: ContentReport, file: Path, buildPaths: BuildPaths, extraRoot: Path? = null) {
  Files.createDirectories(file.parent)
  Files.newOutputStream(file).use { out ->
    val writer = JsonFactory().createGenerator(out).setPrettyPrinter(IntelliJDefaultPrettyPrinter())
    writer.use {
      writer.writeStartArray()
      for (entry in contentReport.combined()) {
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
            writeModuleItem(writer, entry, writeReason = true)
          }
          is ModuleTestOutputEntry -> {
            writer.writeStringField("module", entry.moduleName)
          }
          is ProjectLibraryEntry -> {
            writer.writeStringField("library", entry.data.libraryName)
            writer.writeStringField("libraryFile", shortenAndNormalizePath(entry.libraryFile!!, buildPaths, extraRoot))
            writer.writeNumberField("size", entry.size)
          }
          is CustomAssetEntry -> {
          }
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

private fun shortenAndNormalizePath(file: Path, buildPaths: BuildPaths, extraRoot: Path? = null): String {
  val result = shortenPath(file, buildPaths, extraRoot).replace(File.separatorChar, '/')
  return if (result.startsWith("temp/")) result.substring("temp/".length) else result
}

private fun writeModules(
  writer: JsonGenerator,
  fileEntries: Sequence<DistributionFileEntry>,
  buildPaths: BuildPaths,
  fieldName: String = "modules",
  writeReason: Boolean = true,
) {
  var opened = false
  for (entry in fileEntries) {
    if (entry !is ModuleOutputEntry) {
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

private fun writeModuleLibraries(fileEntries: Sequence<DistributionFileEntry>, moduleName: String, writer: JsonGenerator, buildPaths: BuildPaths) {
  val entriesGroupedByLibraryName = fileEntries
    .filterIsInstance<ModuleLibraryFileEntry>()
    .filter { it.moduleName == moduleName }
    .groupBy { it.libraryName }

  if (entriesGroupedByLibraryName.isEmpty()) {
    return
  }

  writer.writeObjectFieldStart("libraries")
  for ((libName, entries) in entriesGroupedByLibraryName) {
    writer.writeArrayFieldStart(libName)
    for (entry in entries) {
      writer.writeStartObject()
      writer.writeStringField("name", shortenAndNormalizePath(entry.libraryFile!!, buildPaths))
      writer.writeNumberField("size", entry.size)
      writer.writeEndObject()
    }
    writer.writeEndArray()
  }
  writer.writeEndObject()
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