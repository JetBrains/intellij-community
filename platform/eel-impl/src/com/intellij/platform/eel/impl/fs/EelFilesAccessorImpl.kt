// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("RAW_RUN_BLOCKING")

package com.intellij.platform.eel.impl.fs

import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelSharedSecrets
import com.intellij.platform.eel.fs.readFile
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.getOrThrowFileSystemException
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Path

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

  companion object {
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