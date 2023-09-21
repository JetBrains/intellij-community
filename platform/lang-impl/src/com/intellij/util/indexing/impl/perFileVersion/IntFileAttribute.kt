// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.perFileVersion

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes
import com.intellij.openapi.vfs.newvfs.persistent.SpecializedFileAttributes.IntFileAttributeAccessor
import com.intellij.util.io.DataInputOutputUtil

sealed interface IntFileAttribute {
  companion object {
    @JvmStatic
    fun create(id: String, version: Int, fast: Boolean): IntFileAttribute {
      val adjustedVersion = version * 2 + if (fast) 1 else 0
      val attribute = FileAttribute(id, adjustedVersion, true)
      return if (fast) overFastAttribute(attribute) else overRegularAttribute(attribute)
    }

    fun overRegularAttribute(attribute: FileAttribute): IntFileAttribute {
      return IntFileAttributeOverFSAttribute(attribute)
    }

    fun overFastAttribute(attribute: FileAttribute): IntFileAttribute {
      thisLogger().assertTrue(attribute.isFixedSize, "Should be fixed size: $attribute")
      return IntFileAttributeOverFastMappedStorage(attribute)
    }
  }

  fun readInt(fileId: Int): Int
  fun writeInt(fileId: Int, value: Int)
}

class IntFileAttributeOverFastMappedStorage(private val attribute: FileAttribute) : IntFileAttribute {
  private val attributeAccessor: IntFileAttributeAccessor = SpecializedFileAttributes.specializeAsFastInt(FSRecords.getInstance(),
                                                                                                          attribute)

  override fun readInt(fileId: Int): Int {
    return attributeAccessor.read(fileId, 0)
  }

  override fun writeInt(fileId: Int, value: Int) {
    attributeAccessor.write(fileId, value)
  }
}

class IntFileAttributeOverFSAttribute(private val attribute: FileAttribute) : IntFileAttribute {
  override fun readInt(fileId: Int): Int {
    var indexedVersion = 0
    FSRecords.readAttributeWithLock(fileId, attribute).use { stream ->
      if (stream != null) {
        indexedVersion = DataInputOutputUtil.readINT(stream)
      }
    }
    return indexedVersion
  }

  override fun writeInt(fileId: Int, value: Int) {
    FSRecords.writeAttribute(fileId, attribute).use { stream ->
      DataInputOutputUtil.writeINT(stream, value)
    }
  }
}