package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Writes the `packed-modules.yaml` file of one plugin distribution.
 *
 * The file names each jar of the distribution. Under a jar it names the modules and the libraries the packager put into that jar.
 *
 * It uses the format of `com.intellij.platform.distributionContent.FileEntry` to simplify parsing of the file in the build scripts.
 */
internal class PackedModulesWriter(
  private val outputFile: Path,
  private val pluginRoot: Path,
) {
  private val entries = HashMap<String, Entry>()

  fun addModule(jarFile: Path, moduleName: String) {
    getOrCreateEntry(jarFile).modules.add(moduleName)
  }

  fun addModuleLibrary(jarFile: Path, moduleName: String, libraryName: String) {
    getOrCreateEntry(jarFile).moduleLevelLibraries.add(ModuleLevelLibraryEntry(moduleName, libraryName))
  }

  fun addContentModule(jarFile: Path, moduleName: String) {
    getOrCreateEntry(jarFile).contentModules.add(moduleName)
  }

  fun write() {
    val lines = ArrayList<String>()
    for (entry in entries.values.sortedBy { it.path }) {
      lines.add("- name: ${renderYamlScalar(entry.path)}")
      val librariesByModule = entry.moduleLevelLibraries.groupBy { it.moduleName }
      val fileName = entry.path.substringAfterLast('/')
      addModules(lines, "modules", entry.modules, librariesByModule, fileName)
      addModules(lines, "contentModules", entry.contentModules, librariesByModule, fileName)
      val allModules = entry.modules + entry.contentModules
      val librariesWithoutModule = entry.moduleLevelLibraries.filterNot { it.moduleName in allModules }
      require(librariesWithoutModule.size <= 1) {
        "Multiple libraries $librariesWithoutModule are packed in ${entry.path}"
      }
      val separateLibrary = librariesWithoutModule.singleOrNull()
      if (separateLibrary != null) {
        lines.add("  library: ${renderYamlScalar(separateLibrary.libraryName)}")
        lines.add("  module: ${renderYamlScalar(separateLibrary.moduleName)}")
      }
    }
    outputFile.writeText(lines.joinToString("\n"))
  }

  private fun getOrCreateEntry(jarFile: Path): Entry {
    val relativePath = pluginRoot.relativize(jarFile).joinToString("/")
    val existingEntry = entries.get(relativePath)
    if (existingEntry != null) {
      return existingEntry
    }
    val newEntry = Entry(relativePath)
    entries.put(relativePath, newEntry)
    return newEntry
  }

  private fun addModules(lines: MutableList<String>, key: String, modules: Set<String>, librariesByModule: Map<String, List<ModuleLevelLibraryEntry>>, fileName: String) {
    if (modules.isEmpty()) {
      return
    }
    lines.add("  ${key}:")
    for (module in modules.sorted()) {
      lines.add("  - name: ${renderYamlScalar(module)}")
      val libraryEntries = librariesByModule[module]
      if (!libraryEntries.isNullOrEmpty()) {
        lines.add("    libraries:")
        libraryEntries.forEach { libraryEntry ->
          lines.add("      ${renderYamlScalar(libraryEntry.libraryName)}:")
          lines.add("        - name: ${renderYamlScalar(fileName)}")
        }
      }
    }
  }

  private fun renderYamlScalar(value: String): String {
    if (value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == '.' || it == '/' || it == '_' || it == '-' }) {
      return value
    }
    return "'${value.replace("'", "''")}'"
  }

  private data class Entry(
    @JvmField val path: String,
    @JvmField val modules: HashSet<String> = HashSet(),
    @JvmField val contentModules: HashSet<String> = HashSet(),
    @JvmField val moduleLevelLibraries: MutableSet<ModuleLevelLibraryEntry> = HashSet()
  )

  private data class ModuleLevelLibraryEntry(
    val moduleName: String,
    val libraryName: String,
  )
}
