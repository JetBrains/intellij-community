// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.ThreeState
import com.intellij.util.io.zip.JBZipFile
import com.intellij.util.lang.ZipEntryResolverPool
import com.intellij.util.lang.ZipEntryResolverPool.EntryResolver
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Always loads the zip file in a new instance, never shares.
 *
 * Note: returned [EntryResolver] instances are [java.io.Closeable] and must be closed explicitly.
 */
internal class NonShareableJavaZipFilePool : ZipEntryResolverPool {
  override fun load(file: Path): EntryResolver = object : EntryResolver {
    private val zipFile = JBZipFile(Files.newByteChannel(file, StandardOpenOption.READ), StandardCharsets.UTF_8, true, ThreeState.UNSURE)

    override fun loadZipEntry(path: String): InputStream? =
      zipFile.getEntry(if (path[0] == '/') path.substring(1) else path)?.inputStream

    override fun close() = zipFile.close()
  }

  override fun close() { }
}
