// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import java.nio.file.FileStore
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileStoreAttributeView

internal class IjentNioFileStore(
  private val path: EelPath.Absolute,
  private val ijentFsApi: IjentFileSystemApi,
) : FileStore() {

  override fun name(): String =
    "Ijent"

  override fun type(): String? {
    // todo: we probably might return the type of the underlying file store
    // but for now, we protect ourselves form the code that could attempt to do some native shenanigans
    return "Ijent"
  }

  override fun isReadOnly(): Boolean {
    // todo
    return false
  }

  override fun getTotalSpace(): Long {
    return fsBlocking {
      ijentFsApi.getDiskInfo(path).getOrThrowFileSystemException().totalSpace.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
    }
  }

  override fun getUsableSpace(): Long {
    return fsBlocking {
      ijentFsApi.getDiskInfo(path).getOrThrowFileSystemException().availableSpace.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
    }
  }

  override fun getUnallocatedSpace(): Long {
    return fsBlocking {
      ijentFsApi.getDiskInfo(path).getOrThrowFileSystemException().availableSpace.coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
    }
  }

  override fun supportsFileAttributeView(type: Class<out FileAttributeView?>?): Boolean {
    if (type == BasicFileAttributeView::class.java) {
      return true
    }
    when (ijentFsApi) {
      is IjentFileSystemPosixApi -> return type == IjentNioPosixFileAttributeView::class.java
      else -> return false
    }
  }

  override fun supportsFileAttributeView(name: String): Boolean {
    if (name == "basic") {
      return true
    }
    when (ijentFsApi) {
      is IjentFileSystemPosixApi -> return name == "posix"
      else -> return false
    }
  }

  override fun <V : FileStoreAttributeView> getFileStoreAttributeView(type: Class<V?>?): V? {
    TODO("Not yet implemented") // no one uses it
  }

  override fun getAttribute(attribute: String): Any? {
    TODO("Not yet implemented") // no one uses it
  }
}