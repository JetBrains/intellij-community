// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import java.nio.file.FileStore
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileStoreAttributeView

internal class IjentNioFileStore(
  private val ijentFsApi: IjentFileSystemApi,
) : FileStore() {
  override fun name(): String =
    ijentFsApi.toString()

  // TODO: uncomment appropriate part of the test com.intellij.platform.ijent.functional.fs.UnixLikeFileSystemTest.test file store

  override fun type(): String? {
    TODO("Not yet implemented")
  }

  override fun isReadOnly(): Boolean {
    TODO("Not yet implemented")
  }

  override fun getTotalSpace(): Long {
    TODO("Not yet implemented")
  }

  override fun getUsableSpace(): Long {
    TODO("Not yet implemented")
  }

  override fun getUnallocatedSpace(): Long {
    TODO("Not yet implemented")
  }

  override fun supportsFileAttributeView(type: Class<out FileAttributeView?>?): Boolean {
    TODO("Not yet implemented")
  }

  override fun supportsFileAttributeView(name: String): Boolean {
    TODO("Not yet implemented")
  }

  override fun <V : FileStoreAttributeView> getFileStoreAttributeView(type: Class<V?>?): V? {
    TODO("Not yet implemented")
  }

  override fun getAttribute(attribute: String): Any? {
    TODO("Not yet implemented")
  }
}