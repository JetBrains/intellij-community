package com.intellij.tools.build.bazel.ijPluginPackager

import java.nio.ByteBuffer

internal const val MANIFEST_ENTRY_NAME = "META-INF/MANIFEST.MF"

/** The main attributes a jar tool writes for the build and not for the module: singlejar, the rules_kotlin worker and JavaBuilder. */
private val TOOL_ATTRIBUTES: Set<String> = java.util.Set.of("Created-By", "Target-Label", "Injecting-Rule-Kind")

/**
 * Returns the manifest of a module output JAR in the form that goes into a plugin JAR: without the attributes a jar tool wrote for the
 * build, or `null` when nothing but `Manifest-Version` remains in it. A manifest without such attributes is returned as is, byte for byte.
 */
internal fun patchModuleOutputManifest(data: ByteBuffer): ByteBuffer? {
  val lines = splitKeepingTerminators(Charsets.UTF_8.decode(data.duplicate()).toString())
  val kept = ArrayList<String>(lines.size)
  var removed = false
  var moduleAttributes = 0
  var mainSection = true
  var index = 0
  while (index < lines.size) {
    val line = lines[index]
    if (mainSection && isBlank(line)) {
      mainSection = false
    }
    if (!mainSection) {
      kept.add(line)
      index++
      continue
    }
    val name = line.substringBefore(':', missingDelimiterValue = "")
    var next = index + 1
    while (next < lines.size && lines[next].startsWith(" ")) {
      next++
    }
    if (name in TOOL_ATTRIBUTES) {
      removed = true
    }
    else {
      if (name != "Manifest-Version") {
        moduleAttributes++
      }
      for (i in index until next) {
        kept.add(lines[i])
      }
    }
    index = next
  }
  if (!removed) {
    return data
  }
  if (moduleAttributes == 0 && kept.all { isBlank(it) || it.startsWith("Manifest-Version:") }) {
    return null
  }
  return ByteBuffer.wrap(kept.joinToString("").toByteArray(Charsets.UTF_8))
}

private fun isBlank(line: String): Boolean = line == "\n" || line == "\r\n"

private fun splitKeepingTerminators(text: String): List<String> {
  val lines = ArrayList<String>()
  var start = 0
  while (start < text.length) {
    val end = text.indexOf('\n', start)
    if (end < 0) {
      lines.add(text.substring(start))
      break
    }
    lines.add(text.substring(start, end + 1))
    start = end + 1
  }
  return lines
}
