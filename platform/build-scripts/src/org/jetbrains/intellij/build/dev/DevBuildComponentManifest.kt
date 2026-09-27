// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import com.dynatrace.hash4j.hashing.Hashing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.invariantSeparatorsPathString

private const val DEV_BUILD_COMPONENT_MANIFEST_VERSION = 9
private const val COMPONENT_FILE_ENTRY_TYPE = "component-file"
private const val COMPONENT_SYMLINK_ENTRY_TYPE = "symlink"

@Serializable
@ApiStatus.Internal
data class DevBuildComponentEntry(
  @JvmField val relativePath: String,
  @JvmField val type: String,
  @JvmField val hash: Long? = null,
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

/**
 * Hashes every file a producer wrote and records what it found.
 * External transport links become private component files before hashing. Genuine distribution links remain links.
 *
 * Spanned because it is the one part of a producing action whose cost is a property of the action's output rather than
 * of its work: it reads back, single-threaded, the full content of everything just written, in all ten producing
 * actions. `fileCount` and `byteCount` are what the duration has to be read against.
 */
private fun inventoryDevBuildComponent(componentRoot: Path): List<DevBuildComponentEntry> {
  return spanBuilder("inventory dev build component").use { span ->
    val normalizedComponentRoot = componentRoot.toAbsolutePath().normalize()
    val hasher = DevBuildContentHasher()
    val result = ArrayList<DevBuildComponentEntry>()
    var copiedByteCount = 0L
    Files.walkFileTree(normalizedComponentRoot, object : SimpleFileVisitor<Path>() {
      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        val relativePath = normalizedComponentRoot.relativize(file.toAbsolutePath().normalize()).invariantSeparatorsPathString
        if (Files.isSymbolicLink(file)) {
          val target = Files.readSymbolicLink(file)
          if (!target.isAbsolute && file.parent.resolve(target).normalize().startsWith(normalizedComponentRoot)) {
            val normalizedTarget = normalizeDevBuildSymlinkTarget(target)
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
            val realFile = file.toRealPath()
            check(Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS)) {
              "Dev-build component external symbolic link '$relativePath' must resolve to a regular file: $target"
            }
            Files.copy(realFile, file, REPLACE_EXISTING, COPY_ATTRIBUTES)
            val size = Files.size(file)
            copiedByteCount += size
            result.add(
              DevBuildComponentEntry(
                relativePath = relativePath,
                type = COMPONENT_FILE_ENTRY_TYPE,
                hash = hasher.hash(file, size),
                executable = computeDevBuildExecutableBit(file),
              )
            )
          }
        }
        else if (attrs.isRegularFile) {
          result.add(
            DevBuildComponentEntry(
              relativePath = relativePath,
              type = COMPONENT_FILE_ENTRY_TYPE,
              hash = hasher.hash(file.toAbsolutePath().normalize(), attrs.size()),
              executable = computeDevBuildExecutableBit(file),
            )
          )
        }
        return FileVisitResult.CONTINUE
      }
    })

    result.sortWith(DEV_BUILD_COMPONENT_ENTRY_ORDER)
    span.setAttribute("fileCount", result.size.toLong())
    span.setAttribute("hashedFileCount", hasher.fileCount)
    span.setAttribute("byteCount", hasher.byteCount)
    span.setAttribute("copiedByteCount", copiedByteCount)
    result
  }
}

private val DEV_BUILD_COMPONENT_ENTRY_ORDER: Comparator<DevBuildComponentEntry> = compareBy(
  DevBuildComponentEntry::relativePath,
  DevBuildComponentEntry::type,
  DevBuildComponentEntry::hash,
  DevBuildComponentEntry::executable,
  { it.symlinkTarget ?: "" },
)

/** Content hashes by absolute path, and what reading them cost, for the inventory span. */
private class DevBuildContentHasher {
  private val hashes = HashMap<Path, Long>()

  /** How many files were actually read - a path asked for twice is hashed once and counted once. */
  var fileCount: Long = 0
    private set
  var byteCount: Long = 0
    private set

  // not `computeIfAbsent`: only a file whose content was actually read counts towards what the hashing cost
  fun hash(file: Path, size: Long): Long {
    hashes.get(file)?.let { return it }
    val hash = computeDevBuildContentHash(file)
    hashes.put(file, hash)
    fileCount++
    byteCount += size
    return hash
  }
}

private fun computeDevBuildSymlinkHash(target: String): Long {
  return Hashing.xxh3_64().hashBytesToLong(target.toByteArray(StandardCharsets.UTF_8))
}

/**
 * The cleaned slash form of a relative link target, the form the Go collector records.
 * It drops a `.` segment, a repeated slash, and a trailing slash, and it keeps every `..` segment.
 * The OS resolves a `..` segment through the link it follows, so a lexical collapse could change the resolved file.
 * A link to its own directory becomes `.`.
 */
private fun normalizeDevBuildSymlinkTarget(target: Path): String {
  return target.invariantSeparatorsPathString.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/").ifEmpty { "." }
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
