@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package org.jetbrains.intellij.build.dev

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.invariantSeparatorsPathString

@ApiStatus.Internal
data class DevPluginResourceEntry(
  @JvmField val path: String,
  @JvmField val kind: String,
  @JvmField val mode: Int,
  @JvmField val symlinkTarget: String? = null,
)

@ApiStatus.Internal
fun completedResource(context: DevPluginPreparationContext, output: String, content: ByteArray): DevPluginPreparedSource {
  val input = context.writeFile(output, "resource", content)
  return DevPluginPreparedSource(output, listOf(DevPluginExecutionSource(
    kind = "entries", manifest = "keep", entries = listOf(DevPluginPreparedEntry(kind = "file", name = "resource", input = input)),
  )))
}

@ApiStatus.Internal
fun resourceInputPath(artifact: DevPluginArtifact, reference: DevPluginReference): Path {
  val root = Path.of(artifact.root).toRealPath()
  require(if (artifact.kind == "directory") Files.isDirectory(root, NOFOLLOW_LINKS) else Files.isRegularFile(root, NOFOLLOW_LINKS)) {
    "Missing resource artifact or stale root kind: $root"
  }
  var path = root
  if (reference.path.isNotEmpty()) {
    for (part in reference.path.split('/')) {
      path = path.resolve(part)
      require(!Files.isSymbolicLink(path)) { "Resource lookup links are unsupported: $path" }
    }
  }
  return path
}

@ApiStatus.Internal
fun resourceInventory(source: Path): List<DevPluginResourceEntry> {
  val entries = ArrayList<DevPluginResourceEntry>()
  Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
    private fun record(path: Path, attributes: BasicFileAttributes) {
      val kind = when {
        attributes.isSymbolicLink -> "symlink"
        attributes.isDirectory -> "directory"
        attributes.isRegularFile -> "file"
        else -> error("Unsupported resource source type: $path")
      }
      entries.add(DevPluginResourceEntry(
        path = source.relativize(path).invariantSeparatorsPathString,
        kind = kind,
        mode = resourceMode(path),
        symlinkTarget = if (attributes.isSymbolicLink) Files.readSymbolicLink(path).invariantSeparatorsPathString else null,
      ))
    }

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      record(dir, attrs)
      return FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      record(file, attrs)
      return FileVisitResult.CONTINUE
    }
  })
  return entries.sortedBy { it.path }.also(::validateResourceEntries)
}

@ApiStatus.Internal
fun validateResourceEntries(entries: List<DevPluginResourceEntry>) {
  require(entries.isNotEmpty() && entries.first().path.isEmpty()) { "A resource inventory requires its source root" }
  require(entries.map { it.path } == entries.map { it.path }.distinct().sorted()) { "Resource paths must be unique and sorted" }
  val byPath = entries.associateBy { it.path }
  for (entry in entries) {
    if (entry.path.isNotEmpty()) {
      validatePreparationPath(entry.path)
      require(byPath.get(entry.path.substringBeforeLast('/', ""))?.kind == "directory") { "Missing resource parent directory: ${entry.path}" }
    }
    require(entry.kind in setOf("file", "directory", "symlink") && entry.mode in 1..511) { "Unsupported resource type or mode: $entry" }
    require((entry.kind == "symlink") == (entry.symlinkTarget != null)) { "Invalid resource link facts: $entry" }
  }
}

/**
 * The POSIX mode of [path], without a link follow. A file system without the `unix` view has no mode.
 * There a directory or an executable file reports `rwxr-xr-x`, and every other file reports `rw-r--r--`.
 */
@ApiStatus.Internal
fun fileMode(path: Path): Int {
  if (path.fileSystem.supportedFileAttributeViews().contains("unix")) {
    return Files.getAttribute(path, "unix:mode", NOFOLLOW_LINKS) as Int
  }
  return if (Files.isDirectory(path, NOFOLLOW_LINKS) || Files.isExecutable(path)) 493 else 420
}

@ApiStatus.Internal
fun resourceMode(path: Path): Int = fileMode(path) and 4095

@ApiStatus.Internal
fun validateResourceLinks(source: Path, entries: List<DevPluginResourceEntry>) {
  val links = entries.filter { it.kind == "symlink" }
  if (links.isEmpty()) return
  val root = source.toRealPath()
  withResourceScratch { scratch ->
    for ((index, entry) in links.withIndex()) {
      val target = requireNotNull(entry.symlinkTarget)
      require(target.isNotEmpty() && target.none { it == '\\' || it == ':' || it == '\u0000' || it == '\r' || it == '\n' } && !Path.of(target).isAbsolute) {
        "Unsafe resource link '${entry.path}': $target"
      }
      val link = source.resolve(entry.path)
      require(link.parent.resolve(target).normalize().startsWith(source) && link.toRealPath().startsWith(root)) {
        "Resource link escapes its source tree: ${entry.path}"
      }
      val probe = Files.createSymbolicLink(scratch.resolve(index.toString()), Path.of(target))
      require(resourceMode(probe) == entry.mode) { "The resource link mode cannot be reproduced: ${entry.path}" }
    }
  }
}

@ApiStatus.Internal
fun validateResourceAssets(assets: List<PluginPackingAsset>) {
  for (scope in assets.map(PluginPackingAsset::scope).distinct()) {
    validateDevBuildDirectorySpellings(assets.filter { it.scope == scope }.map(PluginPackingAsset::destination))
  }
  val paths = HashSet<Pair<String, String>>()
  val directories = assets.filter { it.kind == "directory" }.mapTo(HashSet()) {
    it.scope to devBuildPathIdentity(it.destination)
  }
  for (asset in assets) {
    validatePreparationPath(asset.destination)
    require(paths.add(asset.scope to devBuildPathIdentity(asset.destination))) { "Resource destination collision: ${asset.destination}" }
  }
  for ((scope, path) in paths) {
    var parent = path.substringBeforeLast('/', "")
    while (parent.isNotEmpty()) {
      val parentKey = scope to parent
      require(parentKey !in paths || parentKey in directories) { "Resource file conflicts with a directory: $parent" }
      parent = parent.substringBeforeLast('/', "")
    }
  }
  for (scope in assets.map(PluginPackingAsset::scope).distinct()) {
    validateDevBuildLinks(assets.filter { it.scope == scope }.mapNotNull { asset ->
      asset.symlinkTarget?.let { asset.destination to it }
    })
  }
}

@ApiStatus.Internal
fun <T> withResourceScratch(action: (Path) -> T): T {
  val scratch = Files.createTempDirectory("dev-plugin-resource-")
  try {
    return action(scratch)
  }
  finally {
    Files.walk(scratch).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
  }
}
