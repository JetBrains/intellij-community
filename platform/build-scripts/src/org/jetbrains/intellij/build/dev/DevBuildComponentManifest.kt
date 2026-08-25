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

private const val DEV_BUILD_COMPONENT_MANIFEST_VERSION = 9
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
  /**
   * Where this file's bytes are, for a component that owns no tree - see [writeSourcedDevBuildComponentManifest].
   *
   * A path as the producer received it, which is a Bazel execution-root-relative one, so the composer resolves it
   * against its own working directory and finds the file its action staged at the same path. `null` for an entry of a
   * component that has a tree, whose bytes are at [relativePath] under that tree.
   *
   * Deliberately outside the fingerprint: it names where bytes came from, and [hash] already says what they are.
   */
  @JvmField val source: String? = null,
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
 * One file of a component that owns no tree: bytes that already exist, and the distribution path they belong at.
 *
 * @param relativePath the file's path in the distribution, `/`-separated.
 * @param source the path the producer was given for the bytes - see [DevBuildComponentEntry.source].
 */
@ApiStatus.Internal
data class DevBuildComponentSourcedFile(
  @JvmField val relativePath: String,
  @JvmField val source: String,
)

/**
 * Writes the manifest of a component that owns no tree, naming where each of its files' bytes already are.
 *
 * The counterpart of [writeDevBuildComponentManifest] for a producer whose whole job is to say where already-packed
 * jars belong: copying them into a tree only for the composer to copy them again writes the distribution twice, so it
 * declares no tree at all and the composer copies each jar straight into the distribution.
 *
 * The mode is recorded by construction rather than measured. Such a file lands in the distribution as an ordinary
 * non-executable file - the composer chmods it, exactly as the collector used to before writing this manifest - so
 * `executable` is false here for the same reason it was false then, and not because of whatever mode Bazel left on the
 * packed jar in its output tree. Reading the mode off the source would make the fingerprint depend on that instead.
 *
 * Such a component declares no main class, no core classpath, no additional modules and no plugins, because deciding
 * any of those needs a product layout - see [DevBuildComponentManifest.mainClass].
 */
@ApiStatus.Internal
fun writeSourcedDevBuildComponentManifest(
  file: Path,
  kind: String,
  platformPrefix: String,
  os: OsFamily,
  arch: JvmArchitecture,
  files: Collection<DevBuildComponentSourcedFile>,
) {
  val manifest = DevBuildComponentManifest(
    kind = kind,
    platformPrefix = platformPrefix,
    os = os.osId,
    arch = arch.name,
    additionalModules = emptyList(),
    mainClass = null,
    coreClassPath = emptyList(),
    pluginCount = 0,
    entries = inventorySourcedDevBuildComponent(files),
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
    val hasher = DevBuildContentHasher()
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
                hash = hasher.hash(realFile, Files.size(realFile)),
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
    result
  }
}

/**
 * Hashes the bytes of a component that owns no tree, at the paths its producer was given.
 *
 * Under the same span name as [inventoryDevBuildComponent], because it is the same cost on the same bytes - reading
 * back the component's whole content, single-threaded - and the two are only readable against each other under one
 * name. `hashedFileCount` below `fileCount` is what this shape adds: one jar placed in several plugins is hashed once,
 * where inventorying a tree hashed each copy of it.
 */
private fun inventorySourcedDevBuildComponent(files: Collection<DevBuildComponentSourcedFile>): List<DevBuildComponentEntry> {
  return spanBuilder("inventory dev build component").blockingUse { span ->
    val hasher = DevBuildContentHasher()
    val result = files.mapTo(ArrayList(files.size)) { file ->
      val source = Path.of(file.source)
      require(Files.isRegularFile(source)) { "Source of '${file.relativePath}' is not a regular file: ${file.source}" }
      DevBuildComponentEntry(
        relativePath = file.relativePath,
        type = COMPONENT_FILE_ENTRY_TYPE,
        hash = hasher.hash(source.toAbsolutePath().normalize(), Files.size(source)),
        // by construction, not measured - see `writeSourcedDevBuildComponentManifest`
        executable = false,
        source = file.source,
      )
    }
    result.sortWith(DEV_BUILD_COMPONENT_ENTRY_ORDER)
    span.setAttribute("fileCount", result.size.toLong())
    span.setAttribute("hashedFileCount", hasher.fileCount)
    span.setAttribute("byteCount", hasher.byteCount)
    result
  }
}

private val DEV_BUILD_COMPONENT_ENTRY_ORDER: Comparator<DevBuildComponentEntry> = compareBy(
  DevBuildComponentEntry::relativePath,
  DevBuildComponentEntry::type,
  DevBuildComponentEntry::hash,
  DevBuildComponentEntry::executable,
  { it.symlinkTarget ?: "" },
  { it.source ?: "" },
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
