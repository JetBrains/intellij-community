// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val DEV_BUILD_COMPONENT_MANIFEST_VERSION = 3

@Serializable
@ApiStatus.Internal
data class DevBuildComponentEntry(
  @JvmField val relativePath: String,
  @JvmField val type: String,
  @JvmField val hash: Long,
)

@Serializable
@ApiStatus.Internal
data class DevBuildComponentManifest(
  @JvmField val version: Int = DEV_BUILD_COMPONENT_MANIFEST_VERSION,
  /** The name of the fragment that produced this component. */
  @JvmField val kind: String,
  @JvmField val platformPrefix: String,
  @JvmField val os: String,
  @JvmField val arch: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val mainClass: String,
  @JvmField val coreClassPath: List<String>,
  /**
   * How many plugins this component contributed to `plugin-classpath.txt`.
   *
   * The count in that file covers the whole distribution and precedes the records, so only the composer can write it -
   * it is the sum over the components, and each one has to report its own share.
   */
  @JvmField val pluginCount: Int = 0,
  @JvmField val entries: List<DevBuildComponentEntry>,
)

private val componentManifestJson = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}

@ApiStatus.Internal
fun writeDevBuildComponentManifest(
  file: Path,
  kind: String,
  platformPrefix: String,
  os: OsFamily,
  arch: JvmArchitecture,
  additionalModules: List<String>,
  mainClass: String,
  coreClassPath: Collection<Path>,
  pluginCount: Int,
  entries: Sequence<DistributionFileEntry>,
  componentRoot: Path,
  projectDir: Path,
) {
  val manifestEntries = normalizeDevBuildComponentEntries(entries, componentRoot, projectDir)
  val manifest = DevBuildComponentManifest(
    kind = kind,
    platformPrefix = platformPrefix,
    os = os.osId,
    arch = arch.name,
    additionalModules = additionalModules,
    mainClass = mainClass,
    coreClassPath = coreClassPath.map { path ->
      if (path.startsWith(componentRoot)) componentRoot.relativize(path).invariantSeparatorsPathString else path.invariantSeparatorsPathString
    },
    pluginCount = pluginCount,
    entries = manifestEntries,
  )
  file.parent?.let { Files.createDirectories(it) }
  Files.writeString(file, componentManifestJson.encodeToString(DevBuildComponentManifest.serializer(), manifest))
}

@ApiStatus.Internal
fun readDevBuildComponentManifest(file: Path): DevBuildComponentManifest {
  val manifest = componentManifestJson.decodeFromString(DevBuildComponentManifest.serializer(), Files.readString(file))
  check(manifest.version == DEV_BUILD_COMPONENT_MANIFEST_VERSION) {
    "Unsupported dev-build component manifest version ${manifest.version} in $file"
  }
  return manifest
}

@ApiStatus.Internal
fun computeIdeFingerprintFromComponents(components: Collection<DevBuildComponentManifest>): String {
  return computeIdeFingerprint(components.flatMap { component ->
    component.entries.map { entry -> IdeFingerprintEntry(entry.relativePath, entry.type, entry.hash) }
  })
}

private fun normalizeDevBuildComponentEntries(
  entries: Sequence<DistributionFileEntry>,
  componentRoot: Path,
  projectDir: Path,
): List<DevBuildComponentEntry> {
  val normalizedComponentRoot = componentRoot.toAbsolutePath().normalize()
  val normalizedProjectDir = projectDir.toAbsolutePath().normalize()
  val contentHashes = HashMap<Path, Long>()
  return entries.map { entry ->
    val relativePath = getRelativeDistributionPath(entry, normalizedComponentRoot, normalizedProjectDir)
    val contentPath = entry.path.toAbsolutePath().normalize()
    DevBuildComponentEntry(
      relativePath = relativePath.invariantSeparatorsPathString,
      type = entry.type,
      hash = if (Files.isRegularFile(contentPath)) {
        contentHashes.computeIfAbsent(contentPath, ::computeDevBuildContentHash)
      }
      else {
        entry.hash
      },
    )
  }.sortedWith(compareBy(DevBuildComponentEntry::relativePath, DevBuildComponentEntry::type, DevBuildComponentEntry::hash)).toList()
}

private fun computeDevBuildContentHash(file: Path): Long {
  val hasher = Hashing.xxh3_64().hashStream()
  val buffer = ByteArray(256 * 1024)
  Files.newInputStream(file).use { input ->
    while (true) {
      val count = input.read(buffer)
      if (count < 0) break
      if (count > 0) hasher.putByteArray(if (count == buffer.size) buffer else buffer.copyOf(count))
    }
  }
  return hasher.asLong
}
