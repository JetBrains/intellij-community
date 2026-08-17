// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.classPath.orderCoreClasspathEntries
import org.jetbrains.intellij.build.classPath.writePluginClassPathCount
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.LinkedHashSet

@ApiStatus.Internal
data class DevBuildComponent(
  @JvmField val root: Path,
  @JvmField val manifest: DevBuildComponentManifest,
  /** This component's share of the `plugin-classpath.txt` records, if it built any plugin. */
  @JvmField val pluginClasspathPart: Path? = null,
)

@ApiStatus.Internal
data class ComposedDevBuild(
  @JvmField val platformPrefix: String,
  @JvmField val mainClass: String,
  @JvmField val additionalModules: List<String>,
  @JvmField val coreClassPath: List<String>,
  @JvmField val fingerprint: String,
)

/**
 * Assembles [components] into one distribution at [target].
 *
 * [expectedFragments], when given, is every fragment the caller wired: composing a subset of them would produce an IDE
 * that starts and then fails somewhere far away, so a component that never arrived is caught here instead.
 * [pluginClasspathPrefix] is the `plugin-classpath.txt` prefix one component was asked to produce; it is required as
 * soon as any component contributed plugins, since the file cannot be written without it.
 */
@ApiStatus.Internal
fun composeDevBuildComponents(
  components: List<DevBuildComponent>,
  target: Path,
  pluginClasspathPrefix: Path? = null,
  expectedFragments: Collection<String> = emptyList(),
): ComposedDevBuild {
  require(components.isNotEmpty()) { "At least one dev-build component is required" }
  val first = components.first().manifest
  for (manifest in components.asSequence().drop(1).map(DevBuildComponent::manifest)) {
    check(manifest.platformPrefix == first.platformPrefix) {
      "Dev-build components have different products: '${first.platformPrefix}' and '${manifest.platformPrefix}'"
    }
    check(manifest.os == first.os && manifest.arch == first.arch) {
      "Dev-build components have different target platforms: '${first.os}/${first.arch}' and '${manifest.os}/${manifest.arch}'"
    }
    check(manifest.mainClass == first.mainClass) {
      "Dev-build components have different IDE main classes: '${first.mainClass}' and '${manifest.mainClass}'"
    }
  }

  if (!expectedFragments.isEmpty()) {
    val present = components.mapTo(HashSet()) { it.manifest.kind }
    val missing = expectedFragments.filterNot(present::contains)
    check(missing.isEmpty()) {
      "Dev-build fragments are missing from the composition: ${missing.joinToString()};" +
      " present: ${present.sorted().joinToString()}"
    }
  }

  Files.createDirectories(target)
  for ((root, _) in components) {
    mergeDevBuildComponent(root, target)
  }

  composePluginClassPath(components = components, target = target, prefix = pluginClasspathPrefix)

  val additionalModules = LinkedHashSet<String>()
  for (manifest in components.map(DevBuildComponent::manifest)) {
    additionalModules.addAll(manifest.additionalModules)
  }
  return ComposedDevBuild(
    platformPrefix = first.platformPrefix,
    mainClass = first.mainClass,
    additionalModules = additionalModules.toList(),
    // Ordering can only happen here: each component sorted the share of the classpath it packed, and the leading jars
    // are not necessarily in the same component as the rest.
    coreClassPath = orderCoreClasspathEntries(components.flatMap { it.manifest.coreClassPath }),
    fingerprint = computeIdeFingerprintFromComponents(components.map { it.manifest }),
  )
}

/**
 * Writes `plugins/plugin-classpath.txt` from the prefix one component produced and the per-plugin records of all of them.
 *
 * The file's plugin count spans the whole distribution and sits between the two, which is why no single component can
 * write it. Record order is free - the reader consumes them sequentially, each one self-describing - so it follows the
 * component order the caller passed, which keeps the composition reproducible.
 */
private fun composePluginClassPath(components: List<DevBuildComponent>, target: Path, prefix: Path?) {
  val parts = components.filter { it.pluginClasspathPart != null }
  if (parts.isEmpty()) {
    return
  }

  val prefixFile = checkNotNull(prefix) {
    "Components contributed plugins (${parts.joinToString { it.manifest.kind }}), so the plugin-classpath prefix is required"
  }

  val pluginCount = components.sumOf { it.manifest.pluginCount }
  val file = target.resolve(PLUGIN_CLASSPATH)
  file.parent?.let { Files.createDirectories(it) }
  DataOutputStream(BufferedOutputStream(Files.newOutputStream(file))).use { out ->
    out.write(Files.readAllBytes(prefixFile))
    writePluginClassPathCount(out = out, pluginCount = pluginCount)
    for (part in parts) {
      out.write(Files.readAllBytes(part.pluginClasspathPart!!))
    }
  }
}

/**
 * Links every file of [source] into [target].
 *
 * A composition is a view, not a second distribution. The fragments already published these bytes as Bazel outputs, and
 * copying them would write another 4 GB per product to say nothing new - so each entry becomes a symlink instead.
 *
 * The link is **relative**, which is the whole point of doing this deliberately. Composition used to copy, and never
 * did: under a sandbox every input is itself a symlink, so the branch below reproduced Bazel's own link verbatim and
 * the copy was dead code. Those links were absolute, naming one machine's output base, which left the composed IDE
 * unusable anywhere but the execution root that built it. A relative link describes the layout instead of the machine,
 * and stays correct wherever that layout is reproduced.
 *
 * Falls back to a copy where a symlink cannot be created - Windows without the privilege - so the result is always a
 * whole distribution rather than a partial one.
 */
@ApiStatus.Internal
fun mergeDevBuildComponent(source: Path, target: Path) {
  mergeDevBuildComponent(source = source, target = target) { destination, file ->
    Files.createSymbolicLink(destination, destination.parent.relativize(file))
  }
}

internal fun mergeDevBuildComponent(
  source: Path,
  target: Path,
  linkFile: (destination: Path, source: Path) -> Unit,
) {
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      Files.createDirectories(target.resolve(source.relativize(dir).toString()))
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val destination = target.resolve(source.relativize(file).toString())
      if (Files.exists(destination)) {
        // Two components claiming one path is normally a fragment-ownership bug, and stays an error. It is not one when
        // the bytes agree: a file registered while the platform layout is built - `DistFile`s like `lib/ijent/…`, which
        // every fragment that builds that layout registers and none of them owns - is produced identically by each of
        // them. Dropping it because more than one produced it is how it went missing from a split distribution before.
        check(mismatchOf(file, destination) == null) {
          "Dev-build components provide different content for '${target.relativize(destination)}': ${mismatchOf(file, destination)}"
        }
        return FileVisitResult.CONTINUE
      }
      // A symlink among the sources - Bazel's own, or a relative one an archive extractor left inside a fragment - is
      // linked like anything else rather than reproduced: pointing at the file resolves through it either way, and
      // copying its target verbatim is what used to put absolute paths into the composition.
      try {
        linkFile(destination, file)
      }
      catch (_: IOException) {
        Files.copy(file, destination, StandardCopyOption.COPY_ATTRIBUTES)
      }
      return FileVisitResult.CONTINUE
    }
  })
}

/**
 * How two files claiming one distribution path differ, or `null` when they do not differ at all.
 *
 * Size first, because that settles almost every real collision without reading a jar twice.
 */
private fun mismatchOf(source: Path, destination: Path): String? {
  val sourceSize = Files.size(source)
  val destinationSize = Files.size(destination)
  if (sourceSize != destinationSize) {
    return "$sourceSize bytes from '$source' against $destinationSize already there"
  }
  return if (Files.mismatch(source, destination) == -1L) null else "the same size, $sourceSize bytes, but different content"
}
