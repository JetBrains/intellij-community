// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.classPath.orderCoreClasspathEntries
import org.jetbrains.intellij.build.classPath.writePluginClassPathCount
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.LinkedHashSet
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
data class DevBuildComponent(
  /**
   * The tree this component's files sit in, or `null` for a component that owns no tree.
   *
   * A component with no tree names each file's bytes where they already are, in
   * [DevBuildComponentEntry.source]. Its manifest is then the only
   * statement of what the component contains. Each entry names a source or an explicit distribution link.
   */
  @JvmField val root: Path?,
  @JvmField val manifest: DevBuildComponentManifest,
  /** This component's share of the `plugin-classpath.txt` records, if it built any plugin. */
  @JvmField val pluginClasspathPart: Path? = null,
  @JvmField val sourceBindings: DevBuildComponentSources? = null,
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
 * [sourceRunfiles] requests launch metadata without copying the component files. Its values name Bazel runfiles.
 */
@ApiStatus.Internal
fun composeDevBuildComponents(
  components: List<DevBuildComponent>,
  target: Path,
  pluginClasspathPrefix: Path? = null,
  expectedFragments: Collection<String> = emptyList(),
  additionalModules: Collection<String> = emptyList(),
  sourceRunfiles: Map<Path, String>? = null,
  sourceDirectoryRunfiles: Map<Path, String> = emptyMap(),
): ComposedDevBuild {
  require(components.isNotEmpty()) { "At least one dev-build component is required" }
  for (component in components) {
    for (entry in component.manifest.entries) {
      validateDevBuildEntryMode(entry)
      validateDevBuildSymlinkSource(entry, component.root, sourceDirectoryRunfiles.keys)
    }
  }
  val first = components.first().manifest
  // A component that only contributes files declares no main class - see `DevBuildComponentManifest.mainClass` - so the
  // distribution's main class comes from the components that do, and they still have to agree.
  val mainClass = checkNotNull(components.firstNotNullOfOrNull { it.manifest.mainClass }) {
    "No dev-build component declares an IDE main class: ${components.joinToString { it.manifest.kind }}"
  }
  // A platform-neutral component - see `DevBuildComponentManifest.isPlatformNeutral` - fits any target platform, so the
  // distribution's platform comes from the first component that names one, and only the components that name one have
  // to agree.
  val platform = components.firstOrNull { !it.manifest.isPlatformNeutral }?.manifest
  for (manifest in components.asSequence().drop(1).map(DevBuildComponent::manifest)) {
    check(manifest.platformPrefix == first.platformPrefix) {
      "Dev-build components have different products: '${first.platformPrefix}' and '${manifest.platformPrefix}'"
    }
    check(manifest.isPlatformNeutral || platform == null || (manifest.os == platform.os && manifest.arch == platform.arch)) {
      "Dev-build components have different target platforms: '${platform?.os}/${platform?.arch}' and '${manifest.os}/${manifest.arch}'"
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
  if (sourceRunfiles == null) {
    mergeDevBuildComponents(components, target, sourceDirectoryRunfiles.keys)
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
  if (sourceRunfiles != null) {
    writeDevBuildLocalLayout(components, target, sourceRunfiles, pluginClasspathFile != null, sourceDirectoryRunfiles)
  }
  return ComposedDevBuild(
    platformPrefix = first.platformPrefix,
    mainClass = mainClass,
    additionalModules = declaredModules.toList(),
    coreClassPath = coreClassPath,
    fingerprint = computeIdeFingerprintFromComponents(
      components = components.map { it.manifest },
      pluginClasspathFile = pluginClasspathFile,
      additionalModules = declaredModules,
    ),
  )
}

private fun mergeDevBuildComponents(components: List<DevBuildComponent>, target: Path, sourceDirectories: Set<Path>) {
  validateDevBuildLinkGraph(components.flatMap { it.manifest.entries })
  val paths = HashSet(listOf("core-classpath.txt", "fingerprint.txt", "local-layout.json", PLUGIN_CLASSPATH))
  val directories = components.flatMap { it.manifest.entries }.filter { it.type == "directory" }
  val directoryPaths = directories.mapTo(HashSet()) { devBuildPathIdentity(it.relativePath) }
  validateDevBuildDirectorySpellings(components.flatMap { it.manifest.entries.map(DevBuildComponentEntry::relativePath) } + paths)
  for (component in components) {
    for (entry in component.manifest.entries) {
      val path = entry.relativePath
      check(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' } &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
        "Dev-build component '${component.manifest.kind}' entry escapes the distribution: $path"
      }
      check(paths.add(devBuildPathIdentity(path))) { "Dev-build components both provide '$path'" }
      entry.symlinkTarget?.let { checkDevBuildDistributionLink(path, it) }
      if (component.root == null && entry.symlinkTarget != null && entry.symlinkSource == null) {
        check(Path.of(entry.symlinkTarget).let { it.toString() == entry.symlinkTarget || it.invariantSeparatorsPathString == entry.symlinkTarget }) {
          "The exporter cannot preserve symbolic link '${entry.relativePath}' with target '${entry.symlinkTarget}'"
        }
      }
    }
  }
  for (path in paths) {
    var parent = path.substringBeforeLast('/', "")
    while (parent.isNotEmpty()) {
      check(parent !in paths || parent in directoryPaths) { "Dev-build component entry '$path' is below another entry: $parent" }
      parent = parent.substringBeforeLast('/', "")
    }
  }
  for (component in components) {
    val root = component.root
    val manifest = component.manifest
    val genuineSymlinks = HashMap<String, String>()
    for (entry in manifest.entries) {
      val symlinkTarget = entry.symlinkTarget ?: continue
      check(genuineSymlinks.put(entry.relativePath, symlinkTarget) == null) {
        "Dev-build component '${manifest.kind}' declares symbolic link '${entry.relativePath}' more than once"
      }
    }
    // one span per component, so that a composition that is slow because of one fragment says which one
    spanBuilder("merge dev build component").setAttribute("kind", manifest.kind).use { span ->
      span.setAttribute("manifestOnly", root == null)
      val merged = if (root == null) {
        copyManifestOnlyComponent(manifest = manifest, target = target, sourceDirectories = sourceDirectories, sourceBindings = component.sourceBindings)
      }
      else {
        mergeDevBuildComponent(source = root, target = target, genuineSymlinks = genuineSymlinks).also {
          for (entry in manifest.entries) {
            if (entry.type == "directory") continue
            val mode = entry.mode ?: continue
            setDistributionFileMode(target.resolve(entry.relativePath), entry.executable, mode)
          }
        }
      }
      span.setAttribute("fileCount", merged.fileCount.toLong())
      span.setAttribute("byteCount", merged.byteCount)
    }
  }
  for (entry in directories.sortedByDescending { it.relativePath }) {
    setDistributionFileMode(target.resolve(entry.relativePath), false, entry.mode)
  }
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

/** What one merged component turned out to be, for the span that measured it. */
internal class MergedDevBuildComponent(@JvmField val fileCount: Int, @JvmField val byteCount: Long)

/**
 * Copies a component that owns no tree straight into [target], from where its manifest says each file's bytes are.
 *
 * The manifest names the files and explicit distribution links. This function does not scan a payload tree.
 * Each link must omit a file source. Its relative target must stay within the distribution.
 * Each destination must stay within the distribution and have exactly one owner.
 *
 * The manifest declares the executable flag. Source permissions do not affect the distribution permissions or fingerprint.
 */
private fun copyManifestOnlyComponent(
  manifest: DevBuildComponentManifest,
  target: Path,
  sourceDirectories: Set<Path>,
  sourceBindings: DevBuildComponentSources?,
): MergedDevBuildComponent {
  val normalizedTarget = target.normalize()
  var byteCount = 0L
  val links = LinkedHashMap<String, DevBuildComponentEntry>()
  for (entry in manifest.entries) {
    val destination = normalizedTarget.resolve(entry.relativePath).normalize()
    check(destination.startsWith(normalizedTarget) && destination != normalizedTarget) {
      "Dev-build component '${manifest.kind}' entry escapes the distribution: ${entry.relativePath}"
    }
    if (entry.type == "directory") {
      check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
        "Dev-build directory '${entry.relativePath}' conflicts with a file or link"
      }
      Files.createDirectories(destination)
      continue
    }
    check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      "Dev-build components both provide '${entry.relativePath}'"
    }
    val symlinkTarget = entry.symlinkTarget
    if (symlinkTarget != null) {
      check(entry.source == null && entry.type == "symlink") {
        "Dev-build component '${manifest.kind}' must declare the symbolic link '${entry.relativePath}' without a file source"
      }
      checkDevBuildDistributionLink(entry.relativePath, symlinkTarget)
      links.put(entry.relativePath, entry)
      continue
    }
    val source = checkNotNull(entry.source) {
      "Dev-build component '${manifest.kind}' declares no tree, so '${entry.relativePath}' must name where its bytes are"
    }
    val staged = Path.of(source)
    check((staged.toString() == source || staged.invariantSeparatorsPathString == source) && staged.none { it.toString() == ".." || it.toString() == "." }) {
      "Dev-build component entry '${entry.relativePath}' has an unsafe source: $source"
    }
    val boundSource = sourceBindings?.resolve(staged, symlink = false)
    // The one failure this shape has that a tree does not: a manifest may name a file the composing action never
    // declared, and then the file is simply not in the sandbox. Said plainly here rather than as a NoSuchFileException.
    check(Files.exists(staged)) {
      "Dev-build component '${manifest.kind}' names '$source' for '${entry.relativePath}', but nothing is staged there" +
      " - the composing action has to declare that file as an input"
    }
    // Follow Bazel's staging link, as the tree walk does: reproducing it would leak the execution root into the result.
    val sourceFile = boundSource ?: staged.toRealPath()
    val absoluteSource = staged.toAbsolutePath().normalize()
    val sourceDirectory = sourceDirectories.map { it.toAbsolutePath().normalize() }
      .filter { absoluteSource != it && absoluteSource.startsWith(it) }.maxByOrNull { it.nameCount }
    val physicalDirectory = sourceBindings?.directory(staged) ?: sourceDirectory?.toRealPath()
    check(physicalDirectory == null || sourceFile.startsWith(physicalDirectory)) {
      "Dev-build component entry '${entry.relativePath}' escapes its declared source directory: $source"
    }
    check(Files.isRegularFile(sourceFile)) { "Dev-build component entry '${entry.relativePath}' does not name a regular file: $source" }
    Files.createDirectories(destination.parent)
    byteCount += Files.size(sourceFile)
    copyAsDistributionFile(source = sourceFile, target = destination, executable = entry.executable, mode = entry.mode)
  }
  for (relativePath in orderDevBuildLinks(links.mapValues { it.value.symlinkTarget!! })) {
    val entry = links.get(relativePath)!!
    val symlinkTarget = entry.symlinkTarget!!
    val destination = normalizedTarget.resolve(relativePath).normalize()
    Files.createDirectories(destination.parent)
    val directory = validateDevBuildSymlinkSource(entry, null, sourceDirectories)
    if (directory == null) {
      Files.createSymbolicLink(destination, Path.of(symlinkTarget))
    }
    else {
      val linkSource = Path.of(entry.symlinkSource!!)
      check(linkSource.toString() == entry.symlinkSource || linkSource.invariantSeparatorsPathString == entry.symlinkSource) {
        "Unsafe symbolic link source: ${entry.symlinkSource}"
      }
      val staged = linkSource.toAbsolutePath().normalize()
      val source = sourceBindings?.resolve(staged, symlink = true) ?: staged
      val physicalDirectory = sourceBindings?.directory(staged) ?: directory.toRealPath()
      check(Files.isSymbolicLink(source) && source.parent.toRealPath().startsWith(physicalDirectory) &&
            Files.readSymbolicLink(source).invariantSeparatorsPathString == symlinkTarget) {
        "Dev-build component link '$relativePath' has stale or escaping symbolic link provenance: $source"
      }
      Files.copy(source, destination, LinkOption.NOFOLLOW_LINKS)
    }
  }
  return MergedDevBuildComponent(fileCount = manifest.entries.size, byteCount = byteCount)
}

/**
 * Orders the links so that a link comes after every link its target path traverses.
 * Windows gives a link the kind of the target that exists at creation, so a link created through a pending link would
 * become a file link. Links that depend on nothing keep their order.
 */
internal fun orderDevBuildLinks(links: Map<String, String>): List<String> {
  val pending = HashSet(links.keys)
  val ordered = ArrayList<String>(links.size)
  var remaining = links.keys.toList()
  while (remaining.isNotEmpty()) {
    val deferred = ArrayList<String>()
    for (name in remaining) {
      if (devBuildLinkTraversesPending(name, links.get(name)!!, pending)) {
        deferred.add(name)
      }
      else {
        pending.remove(name)
        ordered.add(name)
      }
    }
    check(deferred.size != remaining.size) { "Dev-build component symbolic link cycle at '${deferred.first()}'" }
    remaining = deferred
  }
  return ordered
}

/** Reports whether the target path of the link passes through, or ends at, a pending link. */
private fun devBuildLinkTraversesPending(name: String, target: String, pending: Set<String>): Boolean {
  var current = name.substringBeforeLast('/', "")
  for (component in target.split('/')) {
    when (component) {
      "", "." -> continue
      ".." -> current = current.substringBeforeLast('/', "")
      else -> {
        current = if (current.isEmpty()) component else "$current/$component"
        if (current in pending) {
          return true
        }
      }
    }
  }
  return false
}

internal fun mergeDevBuildComponent(
  source: Path,
  target: Path,
  genuineSymlinks: Map<String, String>,
): MergedDevBuildComponent {
  var fileCount = 0
  var byteCount = 0L
  val linksNotSeen = HashSet(genuineSymlinks.keys)
  // The links are created after the walk, in dependency order, so that each link finds the kind of its target.
  val pendingLinks = LinkedHashMap<String, Path>()
  fun recreateGenuineSymlink(relativePath: String, destination: Path, symlinkTarget: String) {
    check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
      "Dev-build components both provide '$relativePath'"
    }
    val linkTarget = Path.of(symlinkTarget)
    check(!linkTarget.isAbsolute && destination.parent.resolve(linkTarget).normalize().startsWith(target.normalize())) {
      "Dev-build component symbolic link '$relativePath' escapes the distribution: $symlinkTarget"
    }
    pendingLinks.put(relativePath, destination)
    linksNotSeen.remove(relativePath)
  }

  // Bazel stages a tree artifact with no file as one symbolic link to the tree. The walk starts at the tree itself, so
  // the link is not visited as a file with an empty relative path.
  val tree = if (Files.isSymbolicLink(source)) source.toRealPath() else source
  Files.walkFileTree(tree, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      val relativePath = tree.relativize(dir).invariantSeparatorsPathString
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
      val relativePath = tree.relativize(file).invariantSeparatorsPathString
      val destination = target.resolve(relativePath)
      check(!Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        "Dev-build components both provide '$relativePath'"
      }

      val genuineSymlinkTarget = genuineSymlinks.get(relativePath)
      if (genuineSymlinkTarget == null) {
        // Follow Bazel's staging link. Reproducing it would leak the execution root into the composed distribution.
        val realFile = file.toRealPath()
        fileCount++
        byteCount += if (attrs.isSymbolicLink) Files.size(realFile) else attrs.size()
        Files.copy(realFile, destination, StandardCopyOption.COPY_ATTRIBUTES)
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
  for (relativePath in orderDevBuildLinks(pendingLinks.keys.associateWith { genuineSymlinks.get(it)!! })) {
    Files.createSymbolicLink(pendingLinks.get(relativePath)!!, Path.of(genuineSymlinks.get(relativePath)!!))
  }
  return MergedDevBuildComponent(fileCount = fileCount, byteCount = byteCount)
}

/** Copies [source] with the declared executable flag without linking back into Bazel outputs. */
private fun copyAsDistributionFile(source: Path, target: Path, executable: Boolean, mode: Int?) {
  // COPY_ATTRIBUTES selects the host's optimized copy path, copy-on-write included, so it is never omitted here.
  Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
  setDistributionFileMode(target, executable, mode)
}

private fun setDistributionFileMode(target: Path, executable: Boolean, mode: Int?) {
  try {
    val permissions = if (mode == null) {
      if (executable) DISTRIBUTION_EXECUTABLE_PERMISSIONS else DISTRIBUTION_FILE_PERMISSIONS
    }
    else {
      PosixFilePermission.entries.filterIndexedTo(EnumSet.noneOf(PosixFilePermission::class.java)) { index, _ ->
        mode and (1 shl (8 - index)) != 0
      }
    }
    Files.setPosixFilePermissions(target, permissions)
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

private val DISTRIBUTION_EXECUTABLE_PERMISSIONS: Set<PosixFilePermission> = EnumSet.copyOf(DISTRIBUTION_FILE_PERMISSIONS).apply {
  add(PosixFilePermission.OWNER_EXECUTE)
  add(PosixFilePermission.GROUP_EXECUTE)
  add(PosixFilePermission.OTHERS_EXECUTE)
}
