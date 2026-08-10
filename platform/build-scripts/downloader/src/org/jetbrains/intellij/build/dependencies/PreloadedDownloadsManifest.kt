// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import com.intellij.platform.bazel.runfiles.BazelRunfiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val MANIFEST_HEADER = "intellij-build-downloads\t1"
private val SHA_256_PATTERN = Regex("[0-9a-f]{64}")

internal data class PreloadedDownload(
  @JvmField val source: Path,
  @JvmField val sha256: String,
)

internal fun getPreloadedDownload(url: String): PreloadedDownload? {
  val configuredPath = System.getProperty(BuildDependenciesConstants.PRELOADED_DOWNLOADS_MANIFEST_PROPERTY) ?: return null
  val path = Path.of(configuredPath).let { if (it.isAbsolute) it else BazelRunfiles.resolveRunfilePath(configuredPath) }
  check(Files.isRegularFile(path)) {
    "Preloaded downloads manifest '$path' does not exist or is not a regular file"
  }

  val lines = Files.readAllLines(path, StandardCharsets.UTF_8)
  check(lines.firstOrNull() == MANIFEST_HEADER) {
    "Preloaded downloads manifest '$path' must start with '$MANIFEST_HEADER'"
  }

  val root = path.parent.normalize()
  val urls = HashSet<String>()
  val names = HashSet<String>()
  var result: PreloadedDownload? = null
  for ((index, line) in lines.drop(1).withIndex()) {
    check(line.isNotBlank()) { "Preloaded downloads manifest '$path' has a blank line at ${index + 2}" }
    val fields = line.split('\t')
    check(fields.size == 3) {
      "Preloaded downloads manifest '$path' line ${index + 2} must contain name, SHA-256, and URL"
    }
    val (name, sha256, declaredUrl) = fields
    check(names.add(name)) { "Preloaded downloads manifest '$path' declares duplicate file name '$name'" }
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
    if (declaredUrl == url) {
      result = PreloadedDownload(source = source, sha256 = sha256)
    }
  }
  return checkNotNull(result) {
    "Build dependency '$url' is not declared in authoritative preloaded downloads manifest '$path'"
  }
}
