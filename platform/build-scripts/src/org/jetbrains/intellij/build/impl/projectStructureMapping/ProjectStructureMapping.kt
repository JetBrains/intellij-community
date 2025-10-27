// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.projectStructureMapping

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.intellij.build.io.ZipFileWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet

internal fun getIncludedModules(entries: Sequence<DistributionFileEntry>): Sequence<String> {
  return entries.mapNotNull { (it as? ModuleOutputEntry)?.owner?.moduleName }.distinct()
}

internal fun buildJarContentReport(contentReport: ContentReport, zipFileWriter: ZipFileWriter, buildPaths: BuildPaths, context: BuildContext) {
  val (fileToEntry, productModules) = groupPlatformEntries(contentReport = contentReport, buildPaths = buildPaths)

  val allModuleSets = TreeMap<String, MutableList<Pair<ModuleItem, List<DistributionFileEntry>>>>()

  // Group modules by their module sets using chain from ModuleItem.moduleSet
  for ((moduleItem, distEntries) in productModules) {
    val chain = moduleItem.moduleSet
    if (chain == null) {
      // Module not in any module set (e.g., additional product modules)
      continue
    }

    // Module should be included in all sets in its chain
    for (setName in chain) {
      allModuleSets.computeIfAbsent(setName) { mutableListOf() }.add(moduleItem to distEntries)
    }
  }

  // Determine root vs nested module sets by using chain from ModuleItem.moduleSet
  // A set is nested if it appears after another set in any chain (e.g., [A, B] means B is nested)
  // All sets after position 0 in any module's chain are considered nested
  val nestedModuleSetNames = productModules
    .mapNotNull { it.first.moduleSet }  // Get all chains
    .flatMap { it.drop(1) }  // Take all sets except the first (root)
    .toSet()

  // Filter to only root module sets (not nested in other product module sets)
  val rootModuleSets = allModuleSets.filterKeys { it !in nestedModuleSetNames }.toSortedMap()

  val platformData = buildPlatformContentReport(
    contentReport = contentReport,
    buildPaths = buildPaths,
    distFiles = context.getDistFiles(os = null, arch = null, libcImpl = null),
    fileToEntry = fileToEntry,
    productModules = productModules,
    moduleSets = rootModuleSets,
  )
  zipFileWriter.uncompressedData("platform.yaml", platformData)
  zipFileWriter.uncompressedData("product-modules.yaml", buildProductModuleContentReport(productModules, buildPaths))

  // Write module set YAMLs with both direct modules and included module sets
  for ((moduleSetName, modules) in allModuleSets) {
    // Use Set to avoid duplicates (same module can appear in multiple JARs)
    val entries = TreeSet<String>()

    // Add direct module names only (modules where this set is the deepest/last in chain)
    val directModules = modules.filter { it.first.moduleSet?.lastOrNull() == moduleSetName }
    entries.addAll(directModules.map { it.first.moduleName })

    // Extract immediate child sets (sets that directly follow this set in any chain)
    // Example: if chain is [parent, current, child, grandchild], only add 'child'
    val nestedSets = modules
      .mapNotNull { it.first.moduleSet }
      .mapNotNull { chain ->
        val currentIndex = chain.indexOf(moduleSetName)
        if (currentIndex != -1 && currentIndex < chain.size - 1) {
          chain[currentIndex + 1]  // Return immediate child
        } else {
          null
        }
      }
      .toSet()
    entries.addAll(nestedSets)

    val out = ByteArrayOutputStream()
    createYamlGenerator(out).use { writer ->
      writer.writeStartArray()
      entries.sorted().forEach(writer::writeString)
      writer.writeEndArray()
    }

    zipFileWriter.uncompressedData("moduleSets/$moduleSetName.yaml", out.toByteArray())
  }

  zipFileWriter.uncompressedData("bundled-plugins.yaml", buildPluginContentReport(contentReport.bundledPlugins, buildPaths))
  zipFileWriter.uncompressedData("non-bundled-plugins.yaml", buildPluginContentReport(contentReport.nonBundledPlugins, buildPaths))
}

private fun buildPluginContentReport(pluginToEntries: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>, buildPaths: BuildPaths): ByteArray {
  val out = ByteArrayOutputStream()
  val writer = createYamlGenerator(out)

  writer.writeStartArray()
  val written = HashSet<String>()
  for ((plugin, entries) in pluginToEntries) {
    val key = plugin.layout.mainModule + (if (plugin.os == null) "" else " (os=${plugin.os})")
    if (!written.add(key)) {
      // duplicate, e.g. OS-specific plugin
      continue
    }

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

private fun buildProductModuleContentReport(productModuleMap: List<Pair<ModuleItem, List<DistributionFileEntry>>>, buildPaths: BuildPaths): ByteArray {
  val out = ByteArrayOutputStream()
  val writer = createYamlGenerator(out)

  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()

  writer.writeStartArray()
  for ((moduleItem, entries) in productModuleMap) {
    fileToPresentablePath.clear()
    fileToEntry.clear()

    for (entry in entries) {
      val file = entry.path
      // the issue is that some modules embedded into some products (Rider), so, name maybe product.jar...
      val presentablePath = if (moduleItem.moduleName.contains(".rd.") && (entry as ModuleOwnedFileEntry).owner!!.moduleName == moduleItem.moduleName) {
        "<file>"
      }
      else {
        fileToPresentablePath.computeIfAbsent(file) {
          shortenAndNormalizePath(it, buildPaths)
        }
      }
      fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
    }

    writer.writeStartObject()
    writer.writeStringField("mainModule", moduleItem.moduleName)

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

      // module maybe embedded in one product and not embedded in another one (rider case)
      writeModules(writer = writer, fileEntries = fileEntries, buildPaths = buildPaths, writeReason = false)
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

private fun buildPlatformContentReport(
  contentReport: ContentReport,
  buildPaths: BuildPaths,
  distFiles: Collection<DistFile>,
  fileToEntry: Map<String, List<DistributionFileEntry>>,
  productModules: List<Pair<ModuleItem, List<DistributionFileEntry>>>,
  moduleSets: Map<String, List<Pair<ModuleItem, List<DistributionFileEntry>>>>,
): ByteArray {
  val out = ByteArrayOutputStream()
  val writer = createYamlGenerator(out)
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
    item.libcImpl?.let { writer.writeStringField("libc", it.toString()) }

    writer.writeEndObject()
  }

  writer.writeStartObject()

  fun writeWithoutDuplicates(pairs: List<Pair<PluginBuildDescriptor, List<DistributionFileEntry>>>) {
    val written = HashSet<String>()
    for ((plugin, _) in pairs) {
      val key = plugin.layout.mainModule + (if (plugin.os == null) "" else " (os=${plugin.os})")
      if (!written.add(key)) {
        // duplicate, e.g. OS-specific plugin
        continue
      }
      writer.writeString(plugin.layout.mainModule)
    }
  }

  writeProductModules(writer = writer, productModules = productModules, moduleSets = moduleSets, kind = ModuleIncludeReasons.PRODUCT_MODULES)
  writeProductModules(writer = writer, productModules = productModules, moduleSets = moduleSets, kind = ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES)

  writer.writeObjectField("name", "plugins")
  run {
    writer.writeArrayFieldStart("bundled")
    writeWithoutDuplicates(contentReport.bundledPlugins)
    writer.writeEndArray()

    writer.writeArrayFieldStart("nonBundled")
    writeWithoutDuplicates(contentReport.nonBundledPlugins)
    writer.writeEndArray()
  }
  writer.writeEndObject()

  writer.writeEndArray()
  writer.close()
  return out.toByteArray()
}

private fun groupPlatformEntries(
  contentReport: ContentReport,
  buildPaths: BuildPaths,
): Pair<Map<String, List<DistributionFileEntry>>, List<Pair<ModuleItem, List<DistributionFileEntry>>>> {
  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val productModuleToEntries = HashMap<ModuleItem, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && ModuleIncludeReasons.isProductModule(owner.reason)) {
        productModuleToEntries.computeIfAbsent(owner) { mutableListOf() }.add(entry)
        continue
      }
    }

    val presentablePath = fileToPresentablePath.computeIfAbsent(entry.path) {
      shortenAndNormalizePath(it, buildPaths)
    }
    fileToEntry.computeIfAbsent(presentablePath) { mutableListOf() }.add(entry)
  }
  return fileToEntry to productModuleToEntries.toList().sortedBy { it.first.moduleName }
}

private fun writeProductModules(
  writer: YAMLGenerator,
  productModules: List<Pair<ModuleItem, List<DistributionFileEntry>>>,
  kind: String,
  moduleSets: Map<String, List<Pair<ModuleItem, List<DistributionFileEntry>>>>,
) {
  writer.writeArrayFieldStart(if (kind == ModuleIncludeReasons.PRODUCT_MODULES) "productModules" else "productEmbeddedModules")
  if (kind == ModuleIncludeReasons.PRODUCT_MODULES) {
    for (moduleSetName in moduleSets.keys) {
      writer.writeString(moduleSetName)
    }
  }

  // Get all module names that are in module sets USED BY THIS PRODUCT
  // Check if any root set name appears in the module's chain
  val modulesInUsedSets = mutableSetOf<String>()
  for ((item) in productModules) {
    val chainList = item.moduleSet
    if (chainList != null) {
      // Check if any root set is in the chain
      for (moduleSetName in moduleSets.keys) {
        if (moduleSetName in chainList) {
          modulesInUsedSets.add(item.moduleName)
          break
        }
      }
    }
  }

  for ((item) in productModules) {
    // Only write individual modules that aren't in any USED module set
    if (item.reason == kind && item.moduleName !in modulesInUsedSets) {
      writer.writeString(item.moduleName)
    }
  }
  writer.writeEndArray()
}

private fun shortenPath(file: Path, buildPaths: BuildPaths, extraRoot: Path?): String {
  if (file.startsWith(MAVEN_REPO)) {
    return $$"$MAVEN_REPOSITORY$/" + MAVEN_REPO.relativize(file).toString().replace(File.separatorChar, '/')
  }
  val projectHome = buildPaths.projectHome
  if (file.startsWith(projectHome)) {
    return $$"$PROJECT_DIR$/" + projectHome.relativize(file).toString()
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
    val moduleName = entry.owner.moduleName
    writeModuleItem(writer = writer, entry = entry, writeReason = writeReason)
    writeModuleLibraries(fileEntries = fileEntries, moduleName = moduleName, writer = writer, buildPaths = buildPaths)
    writer.writeEndObject()
  }
  if (opened) {
    writer.writeEndArray()
  }
}

private fun writeModuleItem(writer: JsonGenerator, entry: ModuleOutputEntry, writeReason: Boolean) {
  writer.writeStringField("name", entry.owner.moduleName)
  writer.writeNumberField("size", entry.size)
  if (writeReason) {
    val reason = entry.reason ?: return
    // product module is obvious, reduce size (for embedded, we still want to report)
    if (reason != ModuleIncludeReasons.PRODUCT_MODULES) {
      writer.writeStringField("reason", reason)
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
