// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.projectStructureMapping

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildPaths
import org.jetbrains.intellij.build.DistFile
import org.jetbrains.intellij.build.MAVEN_REPO
import org.jetbrains.intellij.build.classPath.PluginBuildDescriptor
import org.jetbrains.intellij.build.impl.ModuleIncludeReasons
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.ProjectLibraryData
import org.jetbrains.intellij.build.io.ZipFileWriter
import tools.jackson.core.JsonGenerator
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.PrettyPrinter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.TreeMap
import java.util.TreeSet

internal fun getIncludedModules(entries: Sequence<DistributionFileEntry>): Sequence<String> {
  return entries.mapNotNull { (it as? ModuleOutputEntry)?.owner?.moduleName }.distinct()
}

private fun buildModuleSetHierarchy(
  productModules: List<Pair<ModuleItem, List<DistributionFileEntry>>>
): Pair<Map<String, List<Pair<ModuleItem, List<DistributionFileEntry>>>>, Map<String, List<Pair<ModuleItem, List<DistributionFileEntry>>>>> {
  val allModuleSets = TreeMap<String, MutableList<Pair<ModuleItem, List<DistributionFileEntry>>>>()
  val nestedModuleSetNames = mutableSetOf<String>()

  // Single pass: Group modules by their module sets AND identify nested sets
  for ((moduleItem, distEntries) in productModules) {
    val chain = moduleItem.moduleSet ?: continue // Module not in any module set

    // Module should be included in all sets in its chain
    // Sets after position 0 are nested (e.g., [A, B] means B is nested)
    for ((index, setName) in chain.withIndex()) {
      allModuleSets.computeIfAbsent(setName) { mutableListOf() }.add(moduleItem to distEntries)
      if (index > 0) {
        nestedModuleSetNames.add(setName)
      }
    }
  }

  // Filter to only root module sets (not nested in other product module sets)
  val rootModuleSets = allModuleSets.filterKeys { it !in nestedModuleSetNames }.toSortedMap()

  return allModuleSets to rootModuleSets
}

internal fun buildJarContentReport(contentReport: ContentReport, zipFileWriter: ZipFileWriter, buildPaths: BuildPaths, context: BuildContext) {
  val (fileToEntry, productModules) = groupPlatformEntries(contentReport = contentReport, buildPaths = buildPaths)
  val (allModuleSets, rootModuleSets) = buildModuleSetHierarchy(productModules)

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

    // Single pass: Add direct modules AND immediate child sets
    for ((moduleItem, _) in modules) {
      val chain = moduleItem.moduleSet ?: continue

      // Add direct module if this is the deepest/last set in chain
      if (chain.lastOrNull() == moduleSetName) {
        entries.add(moduleItem.moduleName)
      }

      // Add immediate child set if exists
      // Example: if chain is [parent, current, child, grandchild], only add 'child'
      val currentIndex = chain.indexOf(moduleSetName)
      if (currentIndex != -1 && currentIndex < chain.size - 1) {
        entries.add(chain[currentIndex + 1])
      }
    }

    val out = ByteArrayOutputStream()
    createYamlGenerator(out).use { writer ->
      writeStringArray(writer, entries)
    }

    zipFileWriter.uncompressedData("moduleSets/$moduleSetName.yaml", out.toByteArray())
  }

  zipFileWriter.uncompressedData("bundled-plugins.yaml", buildPluginContentReport(contentReport.bundledPlugins, buildPaths))
  zipFileWriter.uncompressedData("non-bundled-plugins.yaml", buildPluginContentReport(contentReport.nonBundledPlugins, buildPaths))
}

private fun buildPluginContentReport(pluginToEntries: List<PluginBuildDescriptor>, buildPaths: BuildPaths): ByteArray {
  val out = ByteArrayOutputStream()
  val writer = createYamlGenerator(out)

  writer.writeStartArray()
  val written = HashSet<String>()
  for (plugin in pluginToEntries) {
    if (!written.add(createPluginKey(plugin))) {
      // duplicate, e.g. OS-specific plugin
      continue
    }

    val fileToPresentablePath = HashMap<Path, String>()

    val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
    for (entry in plugin.distribution) {
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

    writePluginStart(writer, plugin)

    val contentModuleReason = "<- ${plugin.layout.mainModule} (plugin content)"

    writeContentEntries(writer, fileToEntry, buildPaths) { w, entries ->
      writeModules(
        writer = w,
        fileEntries = entries,
        buildPaths = buildPaths,
        reasonFilter = { it.reason != contentModuleReason },
      )
      writeModules(
        writer = w,
        fileEntries = entries,
        reasonFilter = { it.reason == contentModuleReason },
        buildPaths = buildPaths,
        fieldName = "contentModules",
        writeReason = false,
      )
    }

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
    writer.writeStringProperty("mainModule", moduleItem.moduleName)

    writeContentEntries(writer = writer, fileToEntry = fileToEntry, buildPaths = buildPaths) { w, entries ->
      // module maybe embedded in one product and not embedded in another one (rider case)
      writeModules(writer = w, fileEntries = entries, buildPaths = buildPaths, writeReason = false)
    }

    writer.writeEndObject()
  }
  writer.writeEndArray()

  writer.close()
  return out.toByteArray()
}

private fun createYamlGenerator(out: ByteArrayOutputStream): JsonGenerator {
  val factory = YAMLFactory.builder()
    .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
    .build()
  val writeContext = object : ObjectWriteContext.Base() {
    override fun tokenStreamFactory() = factory
    override fun getPrettyPrinter(): PrettyPrinter = DefaultPrettyPrinter()
  }
  return factory.createGenerator(writeContext, out)
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
    writer.writeStringProperty("name", filePath)
    writeProjectLibs(entries = fileEntries, writer = writer, buildPaths = buildPaths, isInner = true)
    writeModules(writer = writer, fileEntries = fileEntries, buildPaths = buildPaths)
    writer.writeEndObject()
  }

  for (item in distFiles) {
    writer.writeStartObject()

    writer.writeStringProperty("name", item.relativePath)
    item.os?.let { writer.writeStringProperty("os", it.osId) }
    item.arch?.let { writer.writeStringProperty("arch", it.dirName) }
    item.libcImpl?.let { writer.writeStringProperty("libc", it.toString()) }

    writer.writeEndObject()
  }

  writer.writeStartObject()

  fun writeWithoutDuplicates(pairs: List<PluginBuildDescriptor>) {
    val written = HashSet<String>()
    for (plugin in pairs) {
      if (!written.add(createPluginKey(plugin))) {
        // duplicate, e.g. OS-specific plugin
        continue
      }
      writePlugin(writer, plugin)
    }
  }

  writeProductModules(writer = writer, productModules = productModules, moduleSets = moduleSets, kind = ModuleIncludeReasons.PRODUCT_MODULES)
  writeProductModules(writer = writer, productModules = productModules, moduleSets = moduleSets, kind = ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES)

  writer.writeStringProperty("name", "plugins")
  run {
    writer.writeArrayPropertyStart("bundled")
    writeWithoutDuplicates(contentReport.bundledPlugins)
    writer.writeEndArray()

    writer.writeArrayPropertyStart("nonBundled")
    writeWithoutDuplicates(contentReport.nonBundledPlugins)
    writer.writeEndArray()
  }
  writer.writeEndObject()

  writer.writeEndArray()
  writer.close()
  return out.toByteArray()
}

private fun writePlugin(writer: JsonGenerator, plugin: PluginBuildDescriptor) {
  writePluginStart(writer, plugin)
  writer.writeEndObject()
}

private fun writePluginStart(writer: JsonGenerator, plugin: PluginBuildDescriptor) {
  writer.writeStartObject()
  writer.writeStringProperty("mainModule", plugin.layout.mainModule)
  if (plugin.os != null) {
    writer.writeStringProperty("os", plugin.os.osId)
  }
  if (plugin.arch != null) {
    writer.writeStringProperty("arch", plugin.arch.name)
  }
}

private fun groupPlatformEntries(
  contentReport: ContentReport,
  buildPaths: BuildPaths,
): Pair<Map<String, List<DistributionFileEntry>>, List<Pair<ModuleItem, List<DistributionFileEntry>>>> {
  val fileToEntry = TreeMap<String, MutableList<DistributionFileEntry>>()
  val productModuleToEntries = HashMap<ModuleItem, MutableList<DistributionFileEntry>>()
  val fileToPresentablePath = HashMap<Path, String>()

  // First pass: identify container modules with includeDependencies=true
  val containerModules = HashMap<String, ModuleItem>() // module name -> ModuleItem
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && owner.isProductModule() && owner.includeDependencies) {
        containerModules[owner.moduleName] = owner
      }
    }
  }

  // Build a map from dependency module name to its root container module
  val dependencyToContainer = HashMap<String, ModuleItem>()
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && owner.isProductModule()) {
        val reason = owner.reason
        // Check if this is a dependency module (reason starts with PRODUCT_EMBEDDED_MODULES + " <- ")
        if (reason != null && reason.startsWith(ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES + " <- ")) {
          // Extract the root container module from the reason chain
          // Reason format: "productEmbeddedModule <- dep <- ... <- container"
          // The last element is the root container
          val chain = reason.substring((ModuleIncludeReasons.PRODUCT_EMBEDDED_MODULES + " <- ").length).split(" <- ")
          if (chain.isNotEmpty()) {
            val rootContainerName = chain.last()
            val containerModule = containerModules[rootContainerName]
            if (containerModule != null) {
              dependencyToContainer[owner.moduleName] = containerModule
            }
          }
        }
      }
    }
  }

  // Second pass: group entries, aggregating dependencies into their containers
  for (entry in contentReport.platform) {
    if (entry is ModuleOwnedFileEntry) {
      val owner = entry.owner
      if (owner != null && ModuleIncludeReasons.isProductModule(owner.reason)) {
        // Check if this module is a dependency of a container module
        val targetOwner = dependencyToContainer[owner.moduleName] ?: owner
        productModuleToEntries.computeIfAbsent(targetOwner) { mutableListOf() }.add(entry)
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

private fun collectModulesInUsedSets(
  productModules: List<Pair<ModuleItem, List<DistributionFileEntry>>>,
  moduleSets: Map<String, *>,
): Set<String> {
  val usedSetNames = moduleSets.keys  // Already a Set
  return productModules
    .asSequence()
    .filter { (item) -> item.moduleSet?.any { it in usedSetNames } == true }
    .mapTo(mutableSetOf()) { it.first.moduleName }
}

private fun writeProductModules(
  writer: JsonGenerator,
  productModules: List<Pair<ModuleItem, List<DistributionFileEntry>>>,
  kind: String,
  moduleSets: Map<String, List<Pair<ModuleItem, List<DistributionFileEntry>>>>,
) {
  val fieldName = if (kind == ModuleIncludeReasons.PRODUCT_MODULES) "productModules" else "productEmbeddedModules"
  writer.writeArrayPropertyStart(fieldName)

  if (kind == ModuleIncludeReasons.PRODUCT_MODULES) {
    moduleSets.keys.forEach(writer::writeString)
  }

  val modulesInUsedSets = collectModulesInUsedSets(productModules, moduleSets)

  for ((item) in productModules) {
    // Only write individual modules that aren't in any USED module set
    if (item.reason == kind && item.moduleName !in modulesInUsedSets) {
      writer.writeString(item.moduleName)
    }
  }
  writer.writeEndArray()
}

private fun shortenAndNormalizePath(file: Path, buildPaths: BuildPaths, extraRoot: Path? = null): String {
  val shortened = when {
    file.startsWith(MAVEN_REPO) -> $$"$MAVEN_REPOSITORY$/" + MAVEN_REPO.relativize(file).toString()
    file.startsWith(buildPaths.projectHome) -> $$"$PROJECT_DIR$/" + buildPaths.projectHome.relativize(file).toString()
    file.startsWith(buildPaths.buildOutputDir) -> buildPaths.buildOutputDir.relativize(file).toString()
    extraRoot != null && file.startsWith(extraRoot) -> extraRoot.relativize(file).toString()
    else -> file.toString()
  }

  val normalized = shortened.replace(File.separatorChar, '/')
  return if (normalized.startsWith("temp/")) normalized.substring("temp/".length) else normalized
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
      writer.writeArrayPropertyStart(fieldName)
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
  writer.writeStringProperty("name", entry.owner.moduleName)
  writer.writeNumberProperty("size", entry.size)
  if (writeReason) {
    val reason = entry.reason ?: return
    // product module is obvious, reduce size (for embedded, we still want to report)
    if (reason != ModuleIncludeReasons.PRODUCT_MODULES) {
      writer.writeStringProperty("reason", reason)
    }
  }
}

private fun writeModuleLibraries(fileEntries: List<DistributionFileEntry>, moduleName: String, writer: JsonGenerator, buildPaths: BuildPaths) {
  val filteredEntries = fileEntries.filter { it is ModuleLibraryFileEntry && it.moduleName == moduleName }
  val entriesGroupedByLibraryName = groupLibraryEntries<ModuleLibraryFileEntry>(filteredEntries) { it.libraryName }

  if (entriesGroupedByLibraryName.isEmpty()) {
    return
  }

  writer.writeObjectPropertyStart("libraries")
  for ((libName, entries) in entriesGroupedByLibraryName) {
    writeFiles(writer = writer, entries = entries, buildPaths = buildPaths, arrayFieldName = libName)
  }
  writer.writeEndObject()
}

private fun writeSeparatePackedModuleLibrary(fileEntries: List<DistributionFileEntry>, writer: JsonGenerator, buildPaths: BuildPaths) {
  val entriesGroupedByLibraryName = groupLibraryEntries<ModuleLibraryFileEntry>(fileEntries) { it.libraryName }

  require(entriesGroupedByLibraryName.size == 1) {
    "Expected only one library, but got: $entriesGroupedByLibraryName"
  }

  val (libName, entries) = entriesGroupedByLibraryName.iterator().next()
  writer.writeStringProperty("library", libName)
  writer.writeStringProperty("module", entries.first().moduleName)
  writeFiles(writer = writer, entries = entries, buildPaths = buildPaths)
}

private fun writeFiles(
  writer: JsonGenerator,
  entries: List<LibraryFileEntry>,
  buildPaths: BuildPaths,
  arrayFieldName: String = "files",
) {
  writer.writeArrayPropertyStart(arrayFieldName)
  for (entry in entries) {
    writer.writeStartObject()
    writer.writeStringProperty("name", entry.canonicalLibraryPath ?: shortenAndNormalizePath(entry.libraryFile!!, buildPaths))
    writer.writeNumberProperty("size", entry.size)
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
    writer.writeStringProperty("library", libraryData.libraryName)
    writeFiles(writer = writer, entries = entries, buildPaths = buildPaths)
    writer.writeStringProperty("reason", libraryData.reason)
    return
  }

  writer.writeArrayPropertyStart("projectLibraries")
  for ((data, value) in map) {
    writer.writeStartObject()
    writer.writeStringProperty("name", data.libraryName)
    writer.writeArrayPropertyStart("files")
    for (fileEntry in value) {
      writer.writeStartObject()
      writer.writeStringProperty("name", fileEntry.canonicalLibraryPath ?: shortenAndNormalizePath(file = fileEntry.libraryFile!!, buildPaths = buildPaths, extraRoot = null))
      writer.writeNumberProperty("size", fileEntry.size)
      writer.writeEndObject()
    }
    writer.writeEndArray()
    if (data.reason != null) {
      writer.writeStringProperty("reason", data.reason)
    }
    writeModuleDependents(writer, data)
    writer.writeEndObject()
  }
  writer.writeEndArray()
}

private fun writeModuleDependents(writer: JsonGenerator, data: ProjectLibraryData) {
  writer.writeObjectPropertyStart("dependentModules")
  for ((key, value) in data.dependentModules) {
    writer.writeArrayPropertyStart(key)
    for (moduleName in value.sorted()) {
      writer.writeString(moduleName)
    }
    writer.writeEndArray()
  }
  writer.writeEndObject()
}

// Helper functions for deduplication

private fun createPluginKey(plugin: PluginBuildDescriptor): String {
  return plugin.layout.mainModule + (if (plugin.os == null) "" else " (os=${plugin.os})") + (if (plugin.arch == null) "" else " (arch=${plugin.arch.name})")
}

private fun writeStringArray(writer: JsonGenerator, items: Collection<String>) {
  writer.writeStartArray()
  items.sorted().forEach(writer::writeString)
  writer.writeEndArray()
}

private inline fun <reified T : LibraryFileEntry> groupLibraryEntries(
  fileEntries: List<DistributionFileEntry>,
  crossinline getLibraryName: (T) -> String,
): Map<String, List<T>> {
  val entriesGroupedByLibraryName = LinkedHashMap<String, MutableList<T>>()
  for (entry in fileEntries) {
    if (entry is T) {
      entriesGroupedByLibraryName.computeIfAbsent(getLibraryName(entry)) { ArrayList() }.add(entry)
    }
  }
  return entriesGroupedByLibraryName
}

private inline fun writeContentEntries(
  writer: JsonGenerator,
  fileToEntry: Map<String, List<DistributionFileEntry>>,
  buildPaths: BuildPaths,
  writeModulesBlock: (JsonGenerator, List<DistributionFileEntry>) -> Unit,
) {
  writer.writeArrayPropertyStart("content")
  for ((filePath, fileEntries) in fileToEntry) {
    writer.writeStartObject()
    writer.writeStringProperty("name", filePath)
    writeProjectLibs(entries = fileEntries, writer = writer, buildPaths = buildPaths, isInner = false)

    if (fileEntries.all { it is ModuleLibraryFileEntry }) {
      writeSeparatePackedModuleLibrary(fileEntries = fileEntries, writer = writer, buildPaths = buildPaths)
      writer.writeEndObject()
      continue
    }

    writeModulesBlock(writer, fileEntries)
    writer.writeEndObject()
  }
  writer.writeEndArray()
}
