// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes.IntFileAttributeAccessor
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.Closeable
import java.nio.file.Path

@Internal
sealed interface IntFileAttribute : Closeable {
  companion object {
    @JvmStatic
    fun shouldUseFastAttributes(): Boolean {
      return true
    }

    @JvmStatic
    fun create(id: String, version: Int): IntFileAttribute {
      val fast = shouldUseFastAttributes()
      val suffix = if (fast) ".fast" else ""
      val attribute = FileAttribute(id + suffix, version, true)
      return if (fast) overFastAttribute(attribute) else overRegularAttribute(attribute)
    }

    fun overRegularAttribute(attribute: FileAttribute): IntFileAttribute {
      return IntFileAttributeImpl(attribute, null)
    }

    /**
     * By default, file will be created in "PathManager.getIndexRoot()/fastAttributes/attribute.id"
     */
    fun overFastAttribute(attribute: FileAttribute): IntFileAttribute {
      val attributesFilePath = PathManager.getIndexRoot().resolve("fastAttributes").resolve(attribute.id)
      return overFastAttribute(attribute, attributesFilePath)
    }

    fun overFastAttribute(attribute: FileAttribute, path: Path): IntFileAttribute {
      thisLogger().assertTrue(attribute.isFixedSize, "Should be fixed size: $attribute")
      return IntFileAttributeImpl(attribute, path)
    }
  }

  fun readInt(fileId: Int): Int
  fun writeInt(fileId: Int, value: Int)
}

private class IntFileAttributeImpl(private val attribute: FileAttribute,
                                   fastAttributesPathOrNullForRegularAttributes: Path?) : IntFileAttribute {
  private val attributeAccessor = AutoRefreshingOnVfsCloseRef<IntFileAttributeAccessor> { fsRecords ->
    if (fastAttributesPathOrNullForRegularAttributes != null) {
      SpecializedFileAttributes.specializeAsFastInt(fsRecords, attribute, fastAttributesPathOrNullForRegularAttributes)
    }
    else {
      SpecializedFileAttributes.specializeAsInt(fsRecords, attribute)
    }
  }

  override fun readInt(fileId: Int): Int {
    return attributeAccessor().read(fileId, 0)
  }

  override fun writeInt(fileId: Int, value: Int) {
    attributeAccessor().write(fileId, value)
  }

  override fun close() {
    attributeAccessor.close()
  }
}
