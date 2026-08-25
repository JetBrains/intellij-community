// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.classPath.orderCoreClasspathEntries
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.blockingUse
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.LinkedHashSet
import kotlin.io.path.invariantSeparatorsPathString

private const val DEV_BUILD_COMPONENT_MANIFEST_VERSION = 8
private const val COMPONENT_FILE_ENTRY_TYPE = "component-file"
private const val COMPONENT_SYMLINK_ENTRY_TYPE = "symlink"
private const val GENERATED_CORE_CLASSPATH_ENTRY_TYPE = "generated-core-classpath"
private const val GENERATED_PLUGIN_CLASSPATH_ENTRY_TYPE = "generated-plugin-classpath"
private const val LAUNCH_METADATA_ENTRY_TYPE = "launch-metadata"

@Serializable
@ApiStatus.Internal
data class DevBuildComponentEntry(
  @JvmField val relativePath: String,
  @JvmField val type: String,
  @JvmField val hash: Long,
  /** Whether an ordinary owned file has any POSIX executable bit set. */
  @JvmField val executable: Boolean = false,
  /** Relative target of a genuine distribution symlink; `null` for an ordinary owned file. */
  @JvmField val symlinkTarget: String? = null,
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
  /**
   * The IDE main class, or `null` when this component contributes files and nothing else.
   *
   * A component that only carries jars - the content-module jars Bazel packs on their own - knows its product, target
   * platform and files, but the main class follows from `ProductProperties`, and evaluating a product layout is the
   * work such a producer exists to avoid. The composer takes it from a component that does declare one.
   */
  @JvmField val mainClass: String?,
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
  mainClass: String?,
  coreClassPath: Collection<Path>,
  pluginCount: Int,
  componentRoot: Path,
) {
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
    entries = inventoryDevBuildComponent(componentRoot),
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

/**
 * @param additionalModules what the distribution declares it contains, when a caller has that declaration; the
 *                          components' own sum otherwise. It goes into the launch metadata, so a distribution whose
 *                          declaration alone changed gets a new fingerprint and is not reused as the previous one.
 */
@ApiStatus.Internal
fun computeIdeFingerprintFromComponents(
  components: Collection<DevBuildComponentManifest>,
  pluginClasspathFile: Path? = null,
  additionalModules: Collection<String>? = null,
): String {
  require(components.isNotEmpty()) { "At least one dev-build component manifest is required" }
  val first = components.first()
  val launchMetadata = requireNotNull(components.firstOrNull { it.mainClass != null }) {
    "No dev-build component declares an IDE main class"
  }
  val declaredModules = additionalModules
                        ?: components.flatMapTo(LinkedHashSet(), DevBuildComponentManifest::additionalModules)
  val coreClasspath = orderCoreClasspathEntries(components.flatMap(DevBuildComponentManifest::coreClassPath))
  val entries = components.flatMapTo(ArrayList()) { component ->
    component.entries.map { entry -> IdeFingerprintEntry(entry.relativePath, entry.type, entry.hash, entry.executable) }
  }
  entries.add(
    IdeFingerprintEntry(
      relativePath = "<dev-ide-config>",
      type = LAUNCH_METADATA_ENTRY_TYPE,
      hash = computeDevBuildLaunchMetadataHash(
        platformPrefix = first.platformPrefix,
        os = first.os,
        arch = first.arch,
        mainClass = launchMetadata.mainClass!!,
        additionalModules = declaredModules,
      ),
    )
  )
  entries.add(
    IdeFingerprintEntry(
      relativePath = "core-classpath.txt",
      type = GENERATED_CORE_CLASSPATH_ENTRY_TYPE,
      hash = computeDevBuildBytesHash(coreClasspath.joinToString(separator = "\n").toByteArray(StandardCharsets.UTF_8)),
    )
  )
  pluginClasspathFile?.let {
    entries.add(
      IdeFingerprintEntry(
        relativePath = PLUGIN_CLASSPATH,
        type = GENERATED_PLUGIN_CLASSPATH_ENTRY_TYPE,
        hash = computeDevBuildContentHash(it),
      )
    )
  }
  return computeIdeFingerprint(entries)
}

/**
 * Hashes every file a producer wrote and records what it found.
 *
 * Spanned because it is the one part of a producing action whose cost is a property of the action's output rather than
 * of its work: it reads back, single-threaded, the full content of everything just written, in all ten producing
 * actions. `fileCount` and `byteCount` are what the duration has to be read against.
 */
private fun inventoryDevBuildComponent(componentRoot: Path): List<DevBuildComponentEntry> {
  return spanBuilder("inventory dev build component").blockingUse { span ->
    val normalizedComponentRoot = componentRoot.toAbsolutePath().normalize()
    val contentHashes = HashMap<Path, Long>()
    var hashedFileCount = 0L
    var hashedByteCount = 0L
    // not `computeIfAbsent`: only a file whose content was actually read counts towards what the hashing cost
    fun contentHash(file: Path, size: Long): Long {
      contentHashes.get(file)?.let { return it }
      val hash = computeDevBuildContentHash(file)
      contentHashes.put(file, hash)
      hashedFileCount++
      hashedByteCount += size
      return hash
    }

    val result = ArrayList<DevBuildComponentEntry>()
    Files.walkFileTree(normalizedComponentRoot, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val relativePath = normalizedComponentRoot.relativize(file.toAbsolutePath().normalize()).invariantSeparatorsPathString
        if (Files.isSymbolicLink(file)) {
          val target = Files.readSymbolicLink(file)
          if (!target.isAbsolute && file.parent.resolve(target).normalize().startsWith(normalizedComponentRoot)) {
            val normalizedTarget = target.invariantSeparatorsPathString
            result.add(
              DevBuildComponentEntry(
                relativePath = relativePath,
                type = COMPONENT_SYMLINK_ENTRY_TYPE,
                hash = computeDevBuildSymlinkHash(normalizedTarget),
                symlinkTarget = normalizedTarget,
              )
            )
          }
          else {
            // Bazel inputs and cache-backed assets may be linked into the fragment with an absolute or escaping target.
            // Such a link is transport, not distribution semantics: inventory its bytes and let the composer copy them.
            val realFile = file.toRealPath()
            check(Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS)) {
              "Dev-build component external symbolic link '$relativePath' must resolve to a regular file: $target"
            }
            result.add(
              DevBuildComponentEntry(
                relativePath = relativePath,
                type = COMPONENT_FILE_ENTRY_TYPE,
                hash = contentHash(realFile, Files.size(realFile)),
                executable = computeDevBuildExecutableBit(realFile),
              )
            )
          }
        }
        else if (attrs.isRegularFile) {
          result.add(
            DevBuildComponentEntry(
              relativePath = relativePath,
              type = COMPONENT_FILE_ENTRY_TYPE,
              hash = contentHash(file.toAbsolutePath().normalize(), attrs.size()),
              executable = computeDevBuildExecutableBit(file),
            )
          )
        }
        return FileVisitResult.CONTINUE
      }
    })

    result.sortWith(
      compareBy(
        DevBuildComponentEntry::relativePath,
        DevBuildComponentEntry::type,
        DevBuildComponentEntry::hash,
        DevBuildComponentEntry::executable,
        { it.symlinkTarget ?: "" },
      )
    )
    span.setAttribute("fileCount", result.size.toLong())
    span.setAttribute("hashedFileCount", hashedFileCount)
    span.setAttribute("byteCount", hashedByteCount)
    result
  }
}

private fun computeDevBuildSymlinkHash(target: String): Long {
  return Hashing.xxh3_64().hashBytesToLong(target.toByteArray(StandardCharsets.UTF_8))
}

private fun computeDevBuildLaunchMetadataHash(
  platformPrefix: String,
  os: String,
  arch: String,
  mainClass: String,
  additionalModules: Collection<String>,
): Long {
  val hasher = Hashing.xxh3_64().hashStream()
  hasher.putString("dev-launch-v1")
  hasher.putString(platformPrefix)
  hasher.putString(os)
  hasher.putString(arch)
  hasher.putString(mainClass)
  hasher.putInt(additionalModules.size)
  for (module in additionalModules) {
    hasher.putString(module)
  }
  return hasher.asLong
}

private fun computeDevBuildBytesHash(bytes: ByteArray): Long {
  return Hashing.xxh3_64().hashBytesToLong(bytes)
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

internal fun computeDevBuildExecutableBit(file: Path): Boolean {
  if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
    return false
  }
  val permissions = try {
    Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
  }
  catch (_: UnsupportedOperationException) {
    return false
  }
  return PosixFilePermission.OWNER_EXECUTE in permissions ||
         PosixFilePermission.GROUP_EXECUTE in permissions ||
         PosixFilePermission.OTHERS_EXECUTE in permissions
}
