// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.util.io.Decompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory

/**
 * Extracts [archive] (already inside the [target] environment) into [target] — the single place that
 * picks the extraction backend by where [target] lives, with the format detected from the extension.
 *
 * Local target: [Decompressor] (per-entry prefix strip). Non-local (Dev Container, WSL, SSH): one
 * native [com.intellij.platform.eel.EelArchiveApi.extract] call instead of [Decompressor] doing a
 * per-file request over EEL (IJPL-239835), then [flattenSingleRootDirectory] for the prefix it can't
 * strip.
 *
 * @param stripPrefix if non-null, the leading directory of that name is dropped from every entry
 *   (like [Decompressor.removePrefixPath]); e.g. Node's `node-v.../`.
 * @param zipExtensions [Decompressor.Zip.withZipExtensions] for the local zip path (unix modes from
 *   zip extra fields); ignored for the native path, which restores permissions itself.
 */
@ApiStatus.Internal
@Throws(IOException::class)
suspend fun extractArchive(
  archive: Path,
  target: Path,
  stripPrefix: String? = null,
  zipExtensions: Boolean = false,
) {
  val descriptor = target.getEelDescriptor()
  if (descriptor === LocalEelDescriptor) {
    val name = archive.fileName.toString().lowercase()
    val decompressor = when {
      name.endsWith(".zip") -> Decompressor.Zip(archive).let { if (zipExtensions) it.withZipExtensions() else it }

      name.endsWith(".tar")
      || name.endsWith(".tar.gz") || name.endsWith(".tgz")
      || name.endsWith(".tar.bz2") || name.endsWith(".tbz2")
      || name.endsWith(".tar.xz") || name.endsWith(".txz")
        -> Decompressor.Tar(archive)

      else -> throw IOException("Unsupported archive: $archive")
    }
    if (stripPrefix != null) decompressor.removePrefixPath(stripPrefix)
    withContext(Dispatchers.IO) { decompressor.extract(target) }
  }
  else {
    descriptor.toEelApi().archive.extract(archive.asEelPath(), target.asEelPath())
    if (stripPrefix != null) flattenSingleRootDirectory(target, stripPrefix)
  }
}

/**
 * Strips a single leading [wrapperName] directory by renaming each of its children up one level — the
 * post-extraction equivalent of [Decompressor.removePrefixPath], for the native extract that has no
 * prefix-strip. Renames stay within one filesystem and touch only top-level entries.
 */
private fun flattenSingleRootDirectory(target: Path, wrapperName: String) {
  val wrapper = target.resolve(wrapperName)
  if (!wrapper.isDirectory()) return
  Files.newDirectoryStream(wrapper).use { entries ->
    for (entry in entries) {
      Files.move(entry, target.resolve(entry.fileName))
    }
  }
  Files.deleteIfExists(wrapper)
}
