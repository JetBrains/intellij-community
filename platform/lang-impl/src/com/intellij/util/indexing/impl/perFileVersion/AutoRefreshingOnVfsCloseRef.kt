// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.FSRecordsImpl
import com.intellij.util.io.Unmappable
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable

/**
 * Reference to FastFileAttribute (e.g. [com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes.IntFileAttributeAccessor]
 * or [com.intellij.openapi.vfs.newvfs.persistent.mapped.FastFileAttributes.Int4FileAttribute]) will be invalidated on VFS close.
 * [AutoRefreshingOnVfsCloseRef] tracks VFS close events and recreates references automatically after VFS re-mounted.
 */
@ApiStatus.Internal
class AutoRefreshingOnVfsCloseRef<T : Closeable>(private val factory: (FSRecordsImpl) -> T) : Closeable, Unmappable {

  @Volatile
  private var attributeAccessor: T? = null

  operator fun invoke(): T {
    // we need synchronized to make sure that we don't create too many T instances from different threads.
    // attributeAccessor itself is volatile, and will be `null`-ed without synchronized (because attributeAccessor can become invalid
    // immediately after synchronized block finished, so there must be another way to make sure that initialization and shutdown
    // are not running in parallel)
    return attributeAccessor ?: synchronized(this) {
      attributeAccessor?.let { return@synchronized it }

      val fsRecordsImpl = FSRecords.getInstance()
      val newAccessor = factory(fsRecordsImpl)

      attributeAccessor = newAccessor
      fsRecordsImpl.addCloseable(this)

      return@synchronized newAccessor
    }
  }

  override fun close() {
    try {
      val attributeAccessor = attributeAccessor
      attributeAccessor?.close()
    }
    finally {
      attributeAccessor = null //will be re-opened on next invoke() call
    }
  }

  override fun closeAndUnsafelyUnmap() {
    try {
      val attributeAccessor = attributeAccessor
      if (attributeAccessor is Unmappable) {
        attributeAccessor.closeAndUnsafelyUnmap()
      }
      else {
        attributeAccessor?.close()
      }
    }
    finally {
      attributeAccessor = null
    }
  }
}