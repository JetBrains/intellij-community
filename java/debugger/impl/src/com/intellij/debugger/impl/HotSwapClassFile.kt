// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE", "UsePathInsteadOfFile")

package com.intellij.debugger.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.fs.EelFiles
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException


@ApiStatus.Internal
interface HotSwapClassFile {
  @Throws(IOException::class)
  fun loadBytes(): ByteArray

  fun lastModified(): Long

  companion object {
    @JvmStatic
    fun fromFile(file: File): HotSwapClassFile = HotSwapFile(file)

    @JvmStatic
    fun fromVirtualFile(virtualFile: VirtualFile): HotSwapClassFile = VirtualHotSwapFile(virtualFile)

    @JvmStatic
    fun fromBytes(bytes: ByteArray): HotSwapClassFile = ByteArrayHotSwapFile(bytes)
  }
}

// Keep HotSwapFile public for source compatibility with callers that construct it directly.
open class HotSwapFile(private val file: File) : HotSwapClassFile {
  @Throws(IOException::class)
  override fun loadBytes(): ByteArray = EelFiles.readAllBytes(file.toPath())

  override fun lastModified(): Long = file.lastModified()
}

private class VirtualHotSwapFile(private val virtualFile: VirtualFile) : HotSwapClassFile {
  override fun loadBytes(): ByteArray = virtualFile.contentsToByteArray()

  override fun lastModified(): Long = virtualFile.timeStamp
}

private class ByteArrayHotSwapFile(private val bytes: ByteArray) : HotSwapClassFile {
  override fun loadBytes(): ByteArray = bytes

  override fun lastModified(): Long = 0
}
