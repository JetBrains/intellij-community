// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.platform.eel.nioFs.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelSharedSecrets
import com.intellij.platform.eel.fs.EelFileSystemApi.FileWriterCreationMode
import com.intellij.platform.eel.fs.StreamingWriteResult
import com.intellij.platform.eel.fs.WriteOptionsBuilder
import com.intellij.platform.eel.fs.readFile
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.platform.eel.provider.utils.throwFileSystemException
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

internal class EelFilesAccessorImpl : EelSharedSecrets.EelFilesAccessor {
  private val default = EelSharedSecrets.EelFilesAccessor.Default

  @Throws(IOException::class)
  override fun readAllBytes(path: Path): ByteArray {
    if (shouldInvokeOriginal(path)) {
      return default.readAllBytes(path)
    }
    return runBlocking {
      val eelPath = path.asEelPath()
      val r = eelPath.descriptor.toEelApi().fs.readFile(eelPath).getOrThrowFileSystemException()
      assert(r.fullyRead)
      r.bytes.toByteArray()
    }
  }

  @Throws(IOException::class)
  override fun readString(path: Path, cs: Charset): String {
    if (shouldInvokeOriginal(path)) {
      return default.readString(path, cs)
    }
    return String(readAllBytes(path), cs)
  }

  @Throws(IOException::class)
  override fun write(path: Path, bytes: ByteArray, vararg options: OpenOption): Path {
    if (shouldInvokeOriginal(path) || options.any { it !in streamingWriteOptions }) {
      return default.write(path, bytes, *options)
    }
    require(!(APPEND in options && TRUNCATE_EXISTING in options)) { "APPEND + TRUNCATE_EXISTING not allowed" }

    return runBlocking {
      val eelPath = path.asEelPath()
      val writeOptions = WriteOptionsBuilder(eelPath)
        .append(APPEND in options)
        .truncateExisting(options.isEmpty() || TRUNCATE_EXISTING in options)
        .creationMode(when {
          CREATE_NEW in options -> FileWriterCreationMode.ONLY_CREATE
          options.isEmpty() || CREATE in options -> FileWriterCreationMode.ALLOW_CREATE
          else -> FileWriterCreationMode.ONLY_OPEN_EXISTING
        })
        .build()
      val chunks = flow { emit(ByteBuffer.wrap(bytes)) }
      when (val result = eelPath.descriptor.toEelApi().fs.streamingWrite(chunks, writeOptions)) {
        is StreamingWriteResult.Ok -> path
        is StreamingWriteResult.Error -> result.error.throwFileSystemException()
      }
    }
  }

  companion object {
    private val streamingWriteOptions: Set<OpenOption> = setOf(WRITE, APPEND, CREATE, CREATE_NEW, TRUNCATE_EXISTING)

    /**
     * Although functions from this class must behave the same as their nio counterparts,
     * there's still a chance of performance degradations if Eel API is used for the local descriptor,
     * and the functions from this class are for hot code.
     *
     * The registry flag can be enabled and removed only after thorough performance testing.
     */
    internal fun shouldInvokeOriginal(path: Path): Boolean =
      path.getEelDescriptor() == LocalEelDescriptor &&
      (
        !Registry.Companion.`is`("use.generic.functions.for.local.eel", false)
        || path.fileSystem != FileSystems.getDefault()  // It may be a path from ZipFileSystem or something like that.
      )
  }
}
