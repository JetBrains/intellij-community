// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes
import org.jetbrains.annotations.ApiStatus
import java.io.Closeable
import java.nio.file.Path

@ApiStatus.Internal
sealed interface LongFileAttribute : Closeable {
  companion object {
    @JvmStatic
    fun shouldUseFastAttributes(): Boolean {
      return true
    }

    @JvmStatic
    fun create(id: String, version: Int): LongFileAttribute {
      val fast = shouldUseFastAttributes()
      val suffix = if (fast) ".fast" else ""
      val attribute = FileAttribute(id + suffix, version, true)
      return if (fast) overFastAttribute(attribute) else overRegularAttribute(attribute)
    }

    fun overRegularAttribute(attribute: FileAttribute): LongFileAttribute {
      return LongFileAttributeImpl(attribute, null)
    }

    /**
     * By default, file will be created in "PathManager.getIndexRoot()/fastAttributes/attribute.id"
     */
    fun overFastAttribute(attribute: FileAttribute): LongFileAttribute {
      val attributesFilePath = PathManager.getIndexRoot().resolve("fastAttributes").resolve(attribute.id)
      return overFastAttribute(attribute, attributesFilePath)
    }

    fun overFastAttribute(attribute: FileAttribute, path: Path): LongFileAttribute {
      thisLogger().assertTrue(attribute.isFixedSize, "Should be fixed size: $attribute")
      return LongFileAttributeImpl(attribute, path)
    }
  }

  fun readLong(fileId: Int): Long
  fun writeLong(fileId: Int, value: Long)
}

private class LongFileAttributeImpl(private val attribute: FileAttribute,
                                   fastAttributesPathOrNullForRegularAttributes: Path?) : LongFileAttribute {
  private val attributeAccessor = AutoRefreshingOnVfsCloseRef<SpecializedFileAttributes.LongFileAttributeAccessor> { fsRecords ->
    if (fastAttributesPathOrNullForRegularAttributes != null) {
      SpecializedFileAttributes.specializeAsFastLong(fsRecords, attribute, fastAttributesPathOrNullForRegularAttributes)
    }
    else {
      SpecializedFileAttributes.specializeAsLong(fsRecords, attribute)
    }
  }

  override fun readLong(fileId: Int): Long {
    return attributeAccessor().read(fileId, 0)
  }

  override fun writeLong(fileId: Int, value: Long) {
    attributeAccessor().write(fileId, value)
  }

  override fun close() {
    attributeAccessor.close()
  }
}