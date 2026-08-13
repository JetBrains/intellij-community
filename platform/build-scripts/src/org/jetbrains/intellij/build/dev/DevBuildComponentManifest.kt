// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.projectStructureMapping.DistributionFileEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private const val DEV_BUILD_COMPONENT_MANIFEST_VERSION = 1

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
  @JvmField val kind: String,
  @JvmField val platformPrefix: String,
  @JvmField val os: String,
  @JvmField val arch: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val mainClass: String,
  @JvmField val coreClassPath: List<String>,
  @JvmField val entries: List<DevBuildComponentEntry>,
)

private val componentManifestJson = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
}

@ApiStatus.Internal
fun writeDevBuildComponentManifest(
  file: Path,
  kind: DevBuildPart,
  platformPrefix: String,
  os: OsFamily,
  arch: JvmArchitecture,
  additionalModules: List<String>,
  mainClass: String,
  coreClassPath: Collection<Path>,
  entries: Sequence<DistributionFileEntry>,
  componentRoot: Path,
  projectDir: Path,
) {
  val manifestEntries = normalizeDevBuildComponentEntries(entries, componentRoot, projectDir)
  val manifest = DevBuildComponentManifest(
    kind = kind.name.lowercase(),
    platformPrefix = platformPrefix,
    os = os.osId,
    arch = arch.name,
    additionalModules = additionalModules,
    mainClass = mainClass,
    coreClassPath = coreClassPath.map { path ->
      if (path.startsWith(componentRoot)) componentRoot.relativize(path).invariantSeparatorsPathString else path.invariantSeparatorsPathString
    },
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
  return entries.map { entry ->
    val relativePath = entry.relativeOutputFile?.let { Path.of(it).normalize() } ?: run {
      val path = entry.path.toAbsolutePath().normalize()
      when {
        path.startsWith(normalizedComponentRoot) -> normalizedComponentRoot.relativize(path)
        path.startsWith(normalizedProjectDir) -> normalizedProjectDir.relativize(path)
        else -> error("Cannot describe distribution entry outside the component and project roots: ${entry.path}")
      }
    }
    check(!relativePath.isAbsolute && !relativePath.startsWith("..")) {
      "Distribution entry has a non-relative output path '${entry.relativeOutputFile}': ${entry.path}"
    }
    DevBuildComponentEntry(
      relativePath = relativePath.invariantSeparatorsPathString,
      type = entry.type,
      hash = entry.hash,
    )
  }.sortedWith(compareBy(DevBuildComponentEntry::relativePath, DevBuildComponentEntry::type, DevBuildComponentEntry::hash)).toList()
}
