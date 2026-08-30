package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Writes the `packed-modules.yaml` file of one plugin distribution.
 *
 * The file names each jar of the distribution. Under a jar it names the modules the packager put into that jar.
 */
internal class PackedModulesWriter(
  private val outputFile: Path,
  private val pluginRoot: Path,
) {
  private val entries = HashMap<String, Entry>()

  fun addModule(jarFile: Path, moduleName: String) {
    getOrCreateEntry(jarFile).modules.add(moduleName)
  }

  fun addContentModule(jarFile: Path, moduleName: String) {
    getOrCreateEntry(jarFile).contentModules.add(moduleName)
  }

  fun write() {
    val lines = ArrayList<String>()
    for (entry in entries.values.sortedBy { it.path }) {
      lines.add("- name: ${renderYamlScalar(entry.path)}")
      addModules(lines, "modules", entry.modules)
      addModules(lines, "contentModules", entry.contentModules)
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

  private fun addModules(lines: MutableList<String>, key: String, modules: Set<String>) {
    if (modules.isEmpty()) {
      return
    }
    lines.add("  ${key}:")
    for (module in modules.sorted()) {
      lines.add("  - name: ${renderYamlScalar(module)}")
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
  )
}
