// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet

private data class PackedPluginJar(
  @JvmField val key: PrepackedPluginContentKey,
  @JvmField val relativeOutputFile: String,
  @JvmField val source: Path,
)

/** Joins Bazel-built plugin jars to the destinations a fragment validated through `JarPackager`, then copies them. */
@ApiStatus.Internal
fun collectPrepackedPluginContentJars(pluginJarsFile: Path, placementFiles: List<Path>, outputDir: Path): Int {
  return spanBuilder("collect prepacked plugin content jars").blockingUse { span ->
    collectPrepackedPluginContentJars(
      pluginJarsFile = pluginJarsFile,
      placementFiles = placementFiles,
      outputDir = outputDir,
      span = span,
    )
  }
}

private fun collectPrepackedPluginContentJars(
  pluginJarsFile: Path,
  placementFiles: List<Path>,
  outputDir: Path,
  span: Span,
): Int {
  val jars = LinkedHashMap<PrepackedPluginContentKey, PackedPluginJar>()
  readTabSeparated(pluginJarsFile, fieldCount = 4) { fields, lineNumber ->
    val key = PrepackedPluginContentKey(pluginMainModule = fields[0], contentModule = fields[1])
    val relativeOutputFile = validateRelativeOutputFile(key = key, value = fields[2], source = "$pluginJarsFile:$lineNumber")
    val jar = PackedPluginJar(
      key = key,
      relativeOutputFile = relativeOutputFile,
      source = Path.of(fields[3]).toAbsolutePath().normalize(),
    )
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

  val normalizedOutputDir = outputDir.toAbsolutePath().normalize()
  val destinations = HashMap<Path, PrepackedPluginContentKey>()
  var byteCount = 0L
  for (key in placements.keys.sortedWith(compareBy(PrepackedPluginContentKey::pluginMainModule, PrepackedPluginContentKey::contentModule))) {
    val jar = jars.getValue(key)
    val distributionPathString = placements.getValue(key)
    val distributionPath = Path.of(distributionPathString)
    require(!distributionPath.isAbsolute && distributionPath.normalize() == distributionPath && !distributionPath.startsWith("..")) {
      "Placement for $key escapes the distribution: $distributionPathString"
    }
    val expectedSuffix = Path.of("lib").resolve(jar.relativeOutputFile)
    require(distributionPath.startsWith("plugins") && distributionPath.endsWith(expectedSuffix)) {
      "Placement for $key is '$distributionPathString', expected plugins/<directory>/$expectedSuffix"
    }
    val target = normalizedOutputDir.resolve(distributionPath).normalize()
    require(target.startsWith(normalizedOutputDir)) { "Placement for $key escapes $outputDir: $distributionPathString" }
    val previous = destinations.put(target, key)
    require(previous == null) { "Plugin jars $previous and $key both claim $distributionPathString" }
    Files.createDirectories(target.parent)
    byteCount += Files.size(jar.source)
    copyAsDistributionFile(source = jar.source, target = target)
  }
  span.setAttribute("jarCount", placements.size.toLong())
  span.setAttribute("byteCount", byteCount)
  return placements.size
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

/** Copies [source] as an ordinary non-executable distribution file without hard-linking back into Bazel outputs. */
@ApiStatus.Internal
fun copyAsDistributionFile(source: Path, target: Path) {
  Files.copy(source.toRealPath(), target, StandardCopyOption.COPY_ATTRIBUTES)
  try {
    Files.setPosixFilePermissions(target, DISTRIBUTION_FILE_PERMISSIONS)
  }
  catch (_: UnsupportedOperationException) {
  }
}

private val DISTRIBUTION_FILE_PERMISSIONS: Set<PosixFilePermission> = EnumSet.of(
  PosixFilePermission.OWNER_READ,
  PosixFilePermission.OWNER_WRITE,
  PosixFilePermission.GROUP_READ,
  PosixFilePermission.OTHERS_READ,
)
