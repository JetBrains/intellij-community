@file:Suppress("ReplaceGetOrSet", "DestructuringDeclaration")

package org.jetbrains.intellij.build.dev

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.intellij.build.impl.PLUGIN_CLASSPATH
import java.nio.file.Files
import java.nio.file.Path

@Serializable
private data class LocalLayoutEntry(
  @JvmField val path: String,
  @JvmField val runfile: String? = null,
  @JvmField val symlinkTarget: String? = null,
  @JvmField val executable: Boolean = false,
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
) {
  val files = ArrayList<LocalLayoutEntry>()
  val paths = HashSet<String>()
  paths.add("local-layout.json")
  val metadata = listOfNotNull("core-classpath.txt", "fingerprint.txt", PLUGIN_CLASSPATH.takeIf { hasPluginClasspath })
  paths.addAll(metadata)
  for ((root, manifest) in components) {
    for (entry in manifest.entries) {
      val path = entry.relativePath
      checkLocalPath(path)
      check(paths.add(path)) { "Dev-build components both provide '$path'" }
      val symlinkTarget = entry.symlinkTarget
      val runfile = if (symlinkTarget != null) {
        check(root != null) { "A component without a tree cannot declare the symbolic link '$path'" }
        val link = Path.of(symlinkTarget)
        val destination = Path.of(path).parent?.resolve(link) ?: link
        check(symlinkTarget.isNotEmpty() && !link.isAbsolute && symlinkTarget.none { it == '\\' || it == ':' || it == '\u0000' } &&
              !destination.normalize().startsWith("..")) {
          "Dev-build component symbolic link '$path' escapes the distribution: $symlinkTarget"
        }
        null
      }
      else {
        val source = root ?: Path.of(checkNotNull(entry.source) { "Dev-build component entry '$path' has no source" })
        val sourceRunfile = checkNotNull(sourceRunfiles.get(source.toAbsolutePath().normalize())) {
          "Dev-build component entry '$path' names an undeclared source: $source"
        }
        checkLocalPath(sourceRunfile)
        if (root == null) sourceRunfile else "$sourceRunfile/$path"
      }
      files.add(LocalLayoutEntry(path, runfile, symlinkTarget, entry.executable))
    }
  }
  for (path in paths) {
    var parent = path.substringBeforeLast('/', "")
    while (parent.isNotEmpty()) {
      check(parent !in paths) { "Dev-build component entry '$path' is below another entry: $parent" }
      parent = parent.substringBeforeLast('/', "")
    }
  }
  val layout = LocalLayout(files = files, metadata = metadata)
  val json = Json { encodeDefaults = true }
  Files.writeString(target.resolve("local-layout.json"), json.encodeToString(LocalLayout.serializer(), layout))
}

private fun checkLocalPath(path: String) {
  check(path.isNotEmpty() && path.none { it == '\\' || it == ':' || it == '\u0000' } &&
        path.split('/').none { it.isEmpty() || it == "." || it == ".." }) {
    "Invalid path in the local dev layout: $path"
  }
}
