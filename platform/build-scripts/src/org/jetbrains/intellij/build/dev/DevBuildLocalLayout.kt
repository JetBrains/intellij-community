@file:Suppress("ReplaceGetOrSet", "DestructuringDeclaration")

package org.jetbrains.intellij.build.dev

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@Serializable
@OptIn(ExperimentalSerializationApi::class)
private data class LocalLayoutEntry(
  @JvmField val path: String,
  @JvmField val runfile: String? = null,
  @JvmField val symlinkTarget: String? = null,
  @JvmField val executable: Boolean = false,
  @JvmField val mode: Int? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) @JvmField val kind: String = "file",
)

@Serializable
private data class LocalLayout(
  @JvmField val version: Int = 1,
  @JvmField val files: List<LocalLayoutEntry>,
  @JvmField val metadata: List<String>,
)

internal fun writeDevBuildLocalLayout(
  components: List<DevBuildComponent>,
  target: Path,
  sourceRunfiles: Map<Path, String>,
  hasPluginClasspath: Boolean,
  sourceDirectoryRunfiles: Map<Path, String> = emptyMap(),
) {
  validateDevBuildLinkGraph(components.flatMap { it.manifest.entries })
  val files = ArrayList<LocalLayoutEntry>()
  val paths = HashSet<String>()
  paths.add("local-layout.json")
  val metadata = listOfNotNull("core-classpath.txt", "fingerprint.txt", PLUGIN_CLASSPATH.takeIf { hasPluginClasspath })
  paths.addAll(metadata)
  val directories = components.flatMap { it.manifest.entries }.filter { it.type == "directory" }
    .mapTo(HashSet()) { devBuildPathIdentity(it.relativePath) }
  validateDevBuildDirectorySpellings(components.flatMap { it.manifest.entries.map(DevBuildComponentEntry::relativePath) } + metadata)
  for ((root, manifest) in components) {
    for (entry in manifest.entries) {
      validateDevBuildEntryMode(entry)
      val path = entry.relativePath
      checkLocalPath(path)
      check(paths.add(devBuildPathIdentity(path))) { "Dev-build components both provide '$path'" }
      val symlinkTarget = entry.symlinkTarget
      validateDevBuildSymlinkSource(entry, root, sourceDirectoryRunfiles.keys)
      val runfile = if (entry.type == "directory") {
        null
      }
      else if (symlinkTarget != null) {
        check(root != null || (entry.source == null && entry.type == "symlink")) {
          "Dev-build component must declare the symbolic link '$path' without a file source"
        }
        checkDevBuildDistributionLink(path, symlinkTarget)
        null
      }
      else {
        val source = root ?: Path.of(checkNotNull(entry.source) { "Dev-build component entry '$path' has no source" })
        val sourceRunfile = resolveSourceRunfile(source, sourceRunfiles, sourceDirectoryRunfiles, path)
        if (root == null) sourceRunfile else "$sourceRunfile/$path"
      }
      val exactMode = entry.mode?.takeUnless { it == if (entry.executable) 493 else 420 }
      files.add(LocalLayoutEntry(path, runfile, symlinkTarget, entry.executable,
                                if (entry.type == "directory") entry.mode else exactMode,
                                kind = if (entry.type == "directory") "directory" else "file"))
    }
  }
  for (path in paths) {
    var parent = path.substringBeforeLast('/', "")
    while (parent.isNotEmpty()) {
      check(parent !in paths || parent in directories) { "Dev-build component entry '$path' is below another entry: $parent" }
      parent = parent.substringBeforeLast('/', "")
    }
  }
  val layout = LocalLayout(files = files, metadata = metadata)
  val json = Json { encodeDefaults = true }
  Files.writeString(target.resolve("local-layout.json"), json.encodeToString(LocalLayout.serializer(), layout))
}

private fun resolveSourceRunfile(source: Path, files: Map<Path, String>, directories: Map<Path, String>, path: String): String {
  check(source.none { it.toString() == ".." || it.toString() == "." }) { "Dev-build component entry '$path' has an unsafe source: $source" }
  val absolute = source.toAbsolutePath().normalize()
  val exact = files.get(absolute)
  if (exact != null) {
    checkLocalPath(exact)
    return exact
  }
  val directory = directories.keys.filter { absolute != it && absolute.startsWith(it) }.maxByOrNull { it.nameCount }
  check(directory != null) { "Dev-build component entry '$path' names an undeclared source: $source" }
  val runfile = directories.getValue(directory)
  checkLocalPath(runfile)
  val child = directory.relativize(absolute).invariantSeparatorsPathString
  checkLocalPath(child)
  return "$runfile/$child"
}

internal fun validateDevBuildSymlinkSource(entry: DevBuildComponentEntry, componentRoot: Path?, directories: Set<Path>): Path? {
  val source = entry.symlinkSource ?: return null
  check(componentRoot == null && entry.type == "symlink" && entry.symlinkTarget != null && entry.source == null && !entry.executable && entry.mode == null) {
    "Dev-build component link '${entry.relativePath}' has conflicting symbolic link provenance"
  }
  val path = Path.of(source)
  check(source.isNotBlank() && path.none { it.toString() == ".." || it.toString() == "." }) {
    "Dev-build component link '${entry.relativePath}' has unsafe symbolic link provenance: $source"
  }
  val absolute = path.toAbsolutePath().normalize()
  val directory = directories.map { it.toAbsolutePath().normalize() }
    .filter { absolute != it && absolute.startsWith(it) }.maxByOrNull { it.nameCount }
  check(directory != null) { "Dev-build component link '${entry.relativePath}' has undeclared directory provenance: $source" }
  return directory
}

private fun checkLocalPath(path: String) {
  validateDevBuildLocalPath(path)
}
