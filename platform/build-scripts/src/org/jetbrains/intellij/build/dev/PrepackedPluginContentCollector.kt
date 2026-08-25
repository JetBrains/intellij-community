// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private data class PackedPluginJar(
  @JvmField val key: PrepackedPluginContentKey,
  @JvmField val relativeOutputFile: String,
  /** The jar's path as the record gave it - execution-root-relative, and the composer resolves it the same way. */
  @JvmField val source: String,
)

/**
 * Joins Bazel-built plugin jars to the destinations a fragment validated through `JarPackager`.
 *
 * Copies nothing: the result is the component's content stated as pairs of destination and existing bytes, which
 * `writeSourcedDevBuildComponentManifest` turns into the manifest the composer copies from. One jar may be named by
 * several plugins, so the same source appears under more than one destination.
 */
@ApiStatus.Internal
fun collectPrepackedPluginContentJars(pluginJarsFile: Path, placementFiles: List<Path>): List<DevBuildComponentSourcedFile> {
  return spanBuilder("collect prepacked plugin content jars").blockingUse { span ->
    collectPrepackedPluginContentJars(
      pluginJarsFile = pluginJarsFile,
      placementFiles = placementFiles,
      span = span,
    )
  }
}

private fun collectPrepackedPluginContentJars(
  pluginJarsFile: Path,
  placementFiles: List<Path>,
  span: Span,
): List<DevBuildComponentSourcedFile> {
  val jars = LinkedHashMap<PrepackedPluginContentKey, PackedPluginJar>()
  readTabSeparated(pluginJarsFile, fieldCount = 4) { fields, lineNumber ->
    val key = PrepackedPluginContentKey(pluginMainModule = fields[0], contentModule = fields[1])
    val relativeOutputFile = validateRelativeOutputFile(key = key, value = fields[2], source = "$pluginJarsFile:$lineNumber")
    val jar = PackedPluginJar(key = key, relativeOutputFile = relativeOutputFile, source = fields[3])
    val previous = jars.put(key, jar)
    require(previous == null) { "$pluginJarsFile:$lineNumber: duplicate plugin jar relation $key" }
  }

  val placements = LinkedHashMap<PrepackedPluginContentKey, String>()
  for (placementFile in placementFiles) {
    readTabSeparated(placementFile, fieldCount = 3) { fields, lineNumber ->
      val key = PrepackedPluginContentKey(pluginMainModule = fields[0], contentModule = fields[1])
      val previous = placements.put(key, fields[2])
      require(previous == null) { "$placementFile:$lineNumber: duplicate placement for $key (already $previous)" }
    }
  }

  val missing = jars.keys - placements.keys
  val unknown = placements.keys - jars.keys
  require(missing.isEmpty() && unknown.isEmpty()) {
    "Packed plugin jar records and validated placements differ: missing placements ${formatKeys(missing)}," +
    " unknown placements ${formatKeys(unknown)}"
  }

  val destinations = HashMap<String, PrepackedPluginContentKey>()
  var byteCount = 0L
  val result = ArrayList<DevBuildComponentSourcedFile>(placements.size)
  for (key in placements.keys.sortedWith(compareBy(PrepackedPluginContentKey::pluginMainModule, PrepackedPluginContentKey::contentModule))) {
    val jar = jars.getValue(key)
    val distributionPathString = placements.getValue(key)
    val distributionPath = Path.of(distributionPathString)
    // The only escape check there is now: with no output directory to resolve against, a placement is accepted or
    // rejected on its own text. A normalized relative path that does not start with `..` cannot leave the distribution,
    // which is what the resolved-and-compared check used to restate.
    require(!distributionPath.isAbsolute && distributionPath.normalize() == distributionPath && !distributionPath.startsWith("..")) {
      "Placement for $key escapes the distribution: $distributionPathString"
    }
    val expectedSuffix = Path.of("lib").resolve(jar.relativeOutputFile)
    require(distributionPath.startsWith("plugins") && distributionPath.endsWith(expectedSuffix)) {
      "Placement for $key is '$distributionPathString', expected plugins/<directory>/$expectedSuffix"
    }
    val relativePath = distributionPath.invariantSeparatorsPathString
    val previous = destinations.put(relativePath, key)
    require(previous == null) { "Plugin jars $previous and $key both claim $distributionPathString" }
    byteCount += Files.size(Path.of(jar.source))
    result.add(DevBuildComponentSourcedFile(relativePath = relativePath, source = jar.source))
  }
  span.setAttribute("jarCount", placements.size.toLong())
  span.setAttribute("byteCount", byteCount)
  return result
}

private fun validateRelativeOutputFile(key: PrepackedPluginContentKey, value: String, source: String): String {
  val path = Path.of(value)
  require(value.isNotBlank() && !path.isAbsolute && path.normalize() == path && !path.startsWith("..")) {
    "$source: relative output file of $key escapes plugin lib: '$value'"
  }
  return value
}

private fun formatKeys(keys: Collection<PrepackedPluginContentKey>): String {
  return keys.sortedWith(compareBy(PrepackedPluginContentKey::pluginMainModule, PrepackedPluginContentKey::contentModule)).joinToString(
    prefix = "[",
    postfix = "]",
  ) { "${it.pluginMainModule}/${it.contentModule}" }
}

private inline fun readTabSeparated(file: Path, fieldCount: Int, consumer: (List<String>, Int) -> Unit) {
  for ((index, line) in Files.readAllLines(file).withIndex()) {
    if (line.isBlank()) {
      continue
    }
    val fields = line.split('\t')
    require(fields.size == fieldCount) { "$file:${index + 1}: expected $fieldCount tab-separated fields, got ${fields.size}" }
    require(fields.none(String::isBlank)) { "$file:${index + 1}: fields must not be blank" }
    consumer(fields, index + 1)
  }
}
