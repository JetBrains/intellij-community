// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.classPath.orderCoreClasspathEntries
import org.jetbrains.intellij.build.classPath.writePluginClassPathCount
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.LinkedHashSet
import kotlin.io.path.invariantSeparatorsPathString

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
  /**
   * The plugin modules the distribution declares it contains, as its caller stated them.
   *
   * Not the sum of what the components assembled: a module the product bundles is packed by a plugin fragment that
   * several distributions share, so no component's manifest names it, and a consumer that needs it would read the
   * distribution as missing it. What the components assembled is checked against this, never substituted for it.
   */
  @JvmField val additionalModules: List<String>,
  @JvmField val coreClassPath: List<String>,
  @JvmField val fingerprint: String,
)

/**
 * Assembles [components] into one distribution at [target].
 *
 * [expectedFragments], when given, is the exact set of fragments the caller wired: composing a subset or an extra stale
 * fragment would produce the wrong IDE, so missing, unexpected, and duplicate kinds are caught here instead.
 * [pluginClasspathPrefix] is the `plugin-classpath.txt` prefix one component was asked to produce; it is required as
 * soon as any component contributed plugins, since the file cannot be written without it.
 * [additionalModules] is what the distribution declares it contains - see [ComposedDevBuild.additionalModules].
 */
@ApiStatus.Internal
fun composeDevBuildComponents(
  components: List<DevBuildComponent>,
  target: Path,
  pluginClasspathPrefix: Path? = null,
  expectedFragments: Collection<String> = emptyList(),
  additionalModules: Collection<String> = emptyList(),
): ComposedDevBuild {
  require(components.isNotEmpty()) { "At least one dev-build component is required" }
  val first = components.first().manifest
  // A component that only contributes files declares no main class - see `DevBuildComponentManifest.mainClass` - so the
  // distribution's main class comes from the components that do, and they still have to agree.
  val mainClass = checkNotNull(components.firstNotNullOfOrNull { it.manifest.mainClass }) {
    "No dev-build component declares an IDE main class: ${components.joinToString { it.manifest.kind }}"
  }
  for (manifest in components.asSequence().drop(1).map(DevBuildComponent::manifest)) {
    check(manifest.platformPrefix == first.platformPrefix) {
      "Dev-build components have different products: '${first.platformPrefix}' and '${manifest.platformPrefix}'"
    }
    check(manifest.os == first.os && manifest.arch == first.arch) {
      "Dev-build components have different target platforms: '${first.os}/${first.arch}' and '${manifest.os}/${manifest.arch}'"
    }
    check(manifest.mainClass == null || manifest.mainClass == mainClass) {
      "Dev-build components have different IDE main classes: '$mainClass' and '${manifest.mainClass}'"
    }
  }

  val componentsWithNegativePluginCounts = components.filter { it.manifest.pluginCount < 0 }
  check(componentsWithNegativePluginCounts.isEmpty()) {
    "Dev-build components report a negative plugin count: " +
    componentsWithNegativePluginCounts.joinToString { "${it.manifest.kind} (${it.manifest.pluginCount})" }
  }
  val componentsMissingPluginClasspathParts = components.filter {
    it.manifest.pluginCount > 0 && it.pluginClasspathPart == null
  }
  check(componentsMissingPluginClasspathParts.isEmpty()) {
    "Dev-build components report plugins but provide no plugin-classpath records: " +
    componentsMissingPluginClasspathParts.joinToString { "${it.manifest.kind} (${it.manifest.pluginCount})" }
  }

  val presentKindCounts = components.groupingBy { it.manifest.kind }.eachCount()
  val duplicateKinds = presentKindCounts.filterValues { it > 1 }.keys.sorted()
  check(duplicateKinds.isEmpty()) {
    "Dev-build fragment kinds must be unique, but these occur more than once: ${duplicateKinds.joinToString()}"
  }
  if (expectedFragments.isNotEmpty()) {
    val expectedKindCounts = expectedFragments.groupingBy { it }.eachCount()
    val duplicateExpectedKinds = expectedKindCounts.filterValues { it > 1 }.keys.sorted()
    check(duplicateExpectedKinds.isEmpty()) {
      "Expected dev-build fragment kinds must be unique, but these occur more than once: ${duplicateExpectedKinds.joinToString()}"
    }
    val present = presentKindCounts.keys
    val expected = expectedKindCounts.keys
    val missing = (expected - present).sorted()
    val unexpected = (present - expected).sorted()
    check(missing.isEmpty() && unexpected.isEmpty()) {
      buildString {
        append("Dev-build fragments do not match the expected composition")
        if (missing.isNotEmpty()) append("; missing: ").append(missing.joinToString())
        if (unexpected.isNotEmpty()) append("; unexpected: ").append(unexpected.joinToString())
        append("; present: ").append(present.sorted().joinToString())
      }
    }
  }

  Files.createDirectories(target)
  for ((root, manifest) in components) {
    val genuineSymlinks = HashMap<String, String>()
    for (entry in manifest.entries) {
      val symlinkTarget = entry.symlinkTarget ?: continue
      check(genuineSymlinks.put(entry.relativePath, symlinkTarget) == null) {
        "Dev-build component '${manifest.kind}' declares symbolic link '${entry.relativePath}' more than once"
      }
    }
    mergeDevBuildComponent(source = root, target = target, genuineSymlinks = genuineSymlinks)
  }

  val pluginClasspathFile = composePluginClassPath(components = components, target = target, prefix = pluginClasspathPrefix)

  val declaredModules = LinkedHashSet(additionalModules)
  val assembledModules = components.flatMapTo(LinkedHashSet()) { it.manifest.additionalModules }
  check(declaredModules.containsAll(assembledModules)) {
    "Dev-build components assembled plugin modules the distribution does not declare: " +
    "${(assembledModules - declaredModules).sorted()}\n" +
    "  declared: ${declaredModules.sorted()}\n" +
    "  assembled: ${assembledModules.sorted()}"
  }
  val coreClassPath = orderCoreClasspathEntries(components.flatMap { it.manifest.coreClassPath })
  return ComposedDevBuild(
    platformPrefix = first.platformPrefix,
    mainClass = mainClass,
    additionalModules = declaredModules.toList(),
    // Ordering can only happen here: each component sorted the share of the classpath it packed, and the leading jars
    // are not necessarily in the same component as the rest.
    coreClassPath = coreClassPath,
    fingerprint = computeIdeFingerprintFromComponents(
      components = components.map { it.manifest },
      pluginClasspathFile = pluginClasspathFile,
      additionalModules = declaredModules,
    ),
  )
}

/**
 * Writes `plugins/plugin-classpath.txt` from the prefix one component produced and the per-plugin records of all of them.
 *
 * The file's plugin count spans the whole distribution and sits between the two, which is why no single component can
 * write it. Record order is free - the reader consumes them sequentially, each one self-describing - so it follows the
 * component order the caller passed, which keeps the composition reproducible.
 */
private fun composePluginClassPath(components: List<DevBuildComponent>, target: Path, prefix: Path?): Path? {
  val parts = components.filter { it.pluginClasspathPart != null }
  if (parts.isEmpty()) {
    return null
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
  return file
}

/**
 * Materializes every file of [source] into [target].
 *
 * Bazel stages fragment files as symlinks into its execution tree. They are followed and copied, making the composed
 * TreeArtifact self-contained. The JDK uses the host's optimized copy path, including copy-on-write where supported.
 * Only links recorded by the component manifest are distribution semantics and are recreated as links; this distinction
 * keeps JCEF's relative framework links while preventing sandbox/output-base paths from leaking into the result.
 */
@ApiStatus.Internal
fun mergeDevBuildComponent(source: Path, target: Path) {
  mergeDevBuildComponent(source = source, target = target, genuineSymlinks = emptyMap())
}

internal fun mergeDevBuildComponent(
  source: Path,
  target: Path,
  genuineSymlinks: Map<String, String>,
) {
  val linksNotSeen = HashSet(genuineSymlinks.keys)
  fun recreateGenuineSymlink(relativePath: String, destination: Path, symlinkTarget: String) {
    check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      "Dev-build components both provide '$relativePath'"
    }
    val linkTarget = Path.of(symlinkTarget)
    check(!linkTarget.isAbsolute && destination.parent.resolve(linkTarget).normalize().startsWith(target.normalize())) {
      "Dev-build component symbolic link '$relativePath' escapes the distribution: $symlinkTarget"
    }
    Files.createSymbolicLink(destination, linkTarget)
    linksNotSeen.remove(relativePath)
  }

  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      val relativePath = source.relativize(dir).invariantSeparatorsPathString
      val destination = target.resolve(relativePath)
      val genuineSymlinkTarget = genuineSymlinks.get(relativePath)
      if (genuineSymlinkTarget != null) {
        // Bazel may materialize a directory symlink inside a TreeArtifact as the directory it points to. The manifest
        // retains the distribution semantics, so recreate the link and ignore the transport-created subtree.
        recreateGenuineSymlink(relativePath, destination, genuineSymlinkTarget)
        return FileVisitResult.SKIP_SUBTREE
      }
      Files.createDirectories(destination)
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      val relativePath = source.relativize(file).invariantSeparatorsPathString
      val destination = target.resolve(relativePath)
      check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        "Dev-build components both provide '$relativePath'"
      }

      val genuineSymlinkTarget = genuineSymlinks.get(relativePath)
      if (genuineSymlinkTarget == null) {
        // Follow Bazel's staging link. Reproducing it would leak the execution root into the composed distribution.
        Files.copy(file.toRealPath(), destination, StandardCopyOption.COPY_ATTRIBUTES)
      }
      else {
        recreateGenuineSymlink(relativePath, destination, genuineSymlinkTarget)
      }
      return FileVisitResult.CONTINUE
    }
  })
  check(linksNotSeen.isEmpty()) {
    "Dev-build component manifest declares symbolic links absent from $source: ${linksNotSeen.sorted().joinToString()}"
  }
}
