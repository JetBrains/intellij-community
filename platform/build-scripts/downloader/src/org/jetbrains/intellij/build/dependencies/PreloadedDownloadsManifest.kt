// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private const val MANIFEST_HEADER = "intellij-build-downloads\t1"
private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")

/**
 * One row of a preloaded downloads manifest: a build dependency Bazel has already fetched,
 * materialized as a runfile, with the checksum Bazel saw when it did.
 */
@ApiStatus.Internal
data class PreloadedDownload(
  @JvmField val name: String,
  @JvmField val source: Path,
  @JvmField val sha256: String,
  @JvmField val url: String,
)

/**
 * The authoritative inventories of build dependencies Bazel has fetched (`preloaded-downloads-v1.tsv`,
 * written by `write_downloads_repo`), and the single place that reads them.
 *
 * A manifest is an immutable Bazel output, so a parsed one is reused for as long as its bytes stay
 * the same instead of being re-read and re-validated per lookup.
 */
@ApiStatus.Internal
object PreloadedDownloads {
  private val cache = ConcurrentHashMap<Path, CachedManifest>()

  private class CachedManifest(@JvmField val content: ByteArray, @JvmField val entries: Map<String, PreloadedDownload>)

  /**
   * Every entry of the manifest at [manifestPath], keyed by the declared file name - which is also the
   * target name of the Bazel label that exports it.
   */
  fun read(manifestPath: Path): Map<String, PreloadedDownload> {
    check(Files.isRegularFile(manifestPath)) {
      "Preloaded downloads manifest '$manifestPath' does not exist or is not a regular file"
    }
    val content = Files.readAllBytes(manifestPath)
    val cached = cache[manifestPath]
    if (cached != null && cached.content.contentEquals(content)) {
      return cached.entries
    }
    val entries = parse(manifestPath, content)
    cache[manifestPath] = CachedManifest(content, entries)
    return entries
  }

  /**
   * The declared dependency for [url], or `null` when no manifest is configured at all (an ordinary
   * build, which is free to download). Under a manifest, an undeclared URL is an error rather than a
   * cache miss: the manifest is the complete inventory.
   */
  internal fun findByUrl(url: String): PreloadedDownload? {
    val entries = readConfiguredManifest() ?: return null
    return checkNotNull(entries.values.firstOrNull { it.url == url }) {
      "Build dependency '$url' is not declared in authoritative preloaded downloads manifest " +
      "'${System.getProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY)}'"
    }
  }

  /**
   * The declared dependencies of every configured manifest, merged.
   *
   * There is one manifest per Bazel repository, and a dependency set is split across repositories so
   * that bumping one pinned version refetches only its own artifact. Each manifest still resolves its
   * rows against its own directory, which is what makes the split safe.
   */
  private fun readConfiguredManifest(): Map<String, PreloadedDownload>? {
    val configured = System.getProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY) ?: return null
    val paths = configured.split(',').filter { it.isNotBlank() }
    check(paths.isNotEmpty()) {
      "'${BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY}' is set but names no manifest"
    }
    val result = LinkedHashMap<String, PreloadedDownload>()
    val urls = HashMap<String, String>()
    for (configuredPath in paths) {
      val path = Path.of(configuredPath).let { if (it.isAbsolute) it else BazelRunfiles.resolveRunfilePath(configuredPath) }
      for ((name, download) in read(path)) {
        val previousName = result.put(name, download)
        check(previousName == null) { "Preloaded downloads manifest '$path' redeclares file name '$name'" }
        val previousUrl = urls.put(download.url, configuredPath)
        check(previousUrl == null) { "Preloaded downloads manifest '$path' redeclares URL '${download.url}' of '$previousUrl'" }
      }
    }
    return result
  }

  private fun parse(path: Path, content: ByteArray): Map<String, PreloadedDownload> {
    val lines = String(content, StandardCharsets.UTF_8).lineSequence().toMutableList()
    // a trailing newline is not a blank row
    if (lines.lastOrNull()?.isEmpty() == true) {
      lines.removeAt(lines.size - 1)
    }
    check(lines.firstOrNull() == MANIFEST_HEADER) {
      "Preloaded downloads manifest '$path' must start with '$MANIFEST_HEADER'"
    }

    val root = path.parent.normalize()
    val urls = HashSet<String>()
    val result = LinkedHashMap<String, PreloadedDownload>()
    for ((index, line) in lines.subList(1, lines.size).withIndex()) {
      check(line.isNotBlank()) { "Preloaded downloads manifest '$path' has a blank line at ${index + 2}" }
      val fields = line.split('\t')
      check(fields.size == 3) {
        "Preloaded downloads manifest '$path' line ${index + 2} must contain name, SHA-256, and URL"
      }
      val (name, sha256, declaredUrl) = fields
      check(urls.add(declaredUrl)) { "Preloaded downloads manifest '$path' declares duplicate URL '$declaredUrl'" }
      check(SHA_256_PATTERN.matches(sha256)) {
        "Preloaded downloads manifest '$path' line ${index + 2} has invalid SHA-256 '$sha256'"
      }
      val relativePath = Path.of(name)
      check(!relativePath.isAbsolute && relativePath.normalize() == relativePath && !relativePath.startsWith("..")) {
        "Preloaded downloads manifest '$path' line ${index + 2} has unsafe file name '$name'"
      }
      val source = root.resolve(relativePath).normalize()
      check(source.startsWith(root)) {
        "Preloaded downloads manifest '$path' line ${index + 2} escapes its repository: '$name'"
      }
      check(Files.isRegularFile(source)) {
        "Preloaded download '$declaredUrl' points to missing runfile '$source'"
      }
      val previous = result.put(name, PreloadedDownload(name = name, source = source, sha256 = sha256, url = declaredUrl))
      check(previous == null) { "Preloaded downloads manifest '$path' declares duplicate file name '$name'" }
    }
    return result
  }
}
