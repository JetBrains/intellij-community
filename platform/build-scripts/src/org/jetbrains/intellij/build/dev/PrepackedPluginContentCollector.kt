// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

/**
 * Joins Bazel-built plugin jars to the destinations a fragment validated through `JarPackager`.
 *
 * Copies nothing: the result is the component's content stated as pairs of destination and existing bytes, which
 * `writeSourcedDevBuildComponentManifest` turns into the manifest the composer copies from. One jar may be named by
 * several plugins, so the same source appears under more than one destination.
 *
 * Both files are keyed by *(plugin main module, `lib/`-relative destination)*, which is the relation's key and the
 * leading two columns of each line. `pluginJarsFile` adds the jar to place, and a placement file adds where in the
 * distribution this assembly put it.
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
  // Relation to the jar's path as the record gave it: execution-root-relative, and the composer resolves it the same way.
  val jars = LinkedHashMap<PrepackedPluginContentKey, String>()
  readTabSeparated(pluginJarsFile) { fields, lineNumber ->
    val key = readKey(fields, source = "$pluginJarsFile:$lineNumber")
    val previous = jars.put(key, fields[2])
    require(previous == null) { "$pluginJarsFile:$lineNumber: duplicate plugin jar relation $key" }
  }

  val placements = LinkedHashMap<PrepackedPluginContentKey, String>()
  for (placementFile in placementFiles) {
    readTabSeparated(placementFile) { fields, lineNumber ->
      val key = readKey(fields, source = "$placementFile:$lineNumber")
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
  for (key in placements.keys.sortedWith(compareBy(PrepackedPluginContentKey::pluginMainModule, PrepackedPluginContentKey::relativeOutputFile))) {
    val source = jars.getValue(key)
    val distributionPathString = placements.getValue(key)
    val distributionPath = Path.of(distributionPathString)
    // The only escape check there is now: with no output directory to resolve against, a placement is accepted or
    // rejected on its own text. A normalized relative path that does not start with `..` cannot leave the distribution,
    // which is what the resolved-and-compared check used to restate.
    require(!distributionPath.isAbsolute && distributionPath.normalize() == distributionPath && !distributionPath.startsWith("..")) {
      "Placement for $key escapes the distribution: $distributionPathString"
    }
    val expectedSuffix = Path.of("lib").resolve(key.relativeOutputFile)
    require(distributionPath.startsWith("plugins") && distributionPath.endsWith(expectedSuffix)) {
      "Placement for $key is '$distributionPathString', expected plugins/<directory>/$expectedSuffix"
    }
    val relativePath = distributionPath.invariantSeparatorsPathString
    val previous = destinations.put(relativePath, key)
    require(previous == null) { "Plugin jars $previous and $key both claim $distributionPathString" }
    byteCount += Files.size(Path.of(source))
    result.add(DevBuildComponentSourcedFile(relativePath = relativePath, source = source))
  }
  span.setAttribute("jarCount", placements.size.toLong())
  span.setAttribute("byteCount", byteCount)
  return result
}

/** The relation's key, from the leading two columns both formats share. */
private fun readKey(fields: List<String>, source: String): PrepackedPluginContentKey {
  val relativeOutputFile = fields[1]
  val path = Path.of(relativeOutputFile)
  require(!path.isAbsolute && path.normalize() == path && !path.startsWith("..")) {
    "$source: relative output file of ${fields[0]} escapes plugin lib: '$relativeOutputFile'"
  }
  return PrepackedPluginContentKey(pluginMainModule = fields[0], relativeOutputFile = relativeOutputFile)
}

private fun formatKeys(keys: Collection<PrepackedPluginContentKey>): String {
  return keys.sortedWith(compareBy(PrepackedPluginContentKey::pluginMainModule, PrepackedPluginContentKey::relativeOutputFile)).joinToString(
    prefix = "[",
    postfix = "]",
  ) { "${it.pluginMainModule}/${it.relativeOutputFile}" }
}

/**
 * Reads one of the two formats this function joins: the relation's key in the leading two columns, and one value in the
 * third. Both hold three fields, so the count is stated once here rather than at each call.
 */
private inline fun readTabSeparated(file: Path, consumer: (List<String>, Int) -> Unit) {
  val fieldCount = 3
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
