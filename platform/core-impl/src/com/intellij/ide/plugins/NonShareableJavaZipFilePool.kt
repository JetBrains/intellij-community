// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.util.lang.ZipFilePool
import java.io.Closeable
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Always loads the zip file in a new instance, never shares.
 *
 * Note: returned EntryResolvers are Closeable and must be closed explicitly.
 */
internal class NonShareableJavaZipFilePool : ZipFilePool() {
  override fun load(file: Path): EntryResolver {
    val zipFile = ZipFile(file.toFile(), StandardCharsets.UTF_8)
    return object : EntryResolver, Closeable {
      override fun loadZipEntry(path: String): InputStream? {
        val entry = zipFile.getEntry(if (path[0] == '/') path.substring(1) else path) ?: return null
        return zipFile.getInputStream(entry)
      }

      override fun close() {
        zipFile.close()
      }
    }
  }

  override fun loadZipFile(file: Path): Any {
    throw IllegalStateException("Should not be called")
  }
}