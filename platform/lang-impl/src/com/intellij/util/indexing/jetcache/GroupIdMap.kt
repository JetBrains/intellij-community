// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.jetcache

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.DataInputOutputUtil
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentHashMap
import org.jetbrains.jetcache.JcHash
import java.io.*
import java.util.*
import kotlin.experimental.and

class GroupIdMap(file: File): Closeable {
  override fun close() {
    map.close()
  }

  val map = PersistentHashMap<JcHash, Array<ByteArray>>(file, JcHashExternalizer, ArrayOfBytesExternalizer)

  fun getMultiple(jcHash: JcHash): Array<ByteArray> {
    return map.get(jcHash)
  }

  fun append(jcHash: JcHash, values: Array<ByteArray>) {
    map.appendData(jcHash, PersistentHashMap.ValueDataAppender {
      for (bytes in values) {
        JcHashExternalizer.save(it, bytes)
      }
    })
  }

  object ArrayOfBytesExternalizer : DataExternalizer<Array<ByteArray>> {
    override fun read(`in`: DataInput): Array<ByteArray> {
      val result = mutableListOf<ByteArray>()
      while ((`in` as InputStream).available() > 0) {
        result.add(JcHashExternalizer.read(`in`))
      }
      return result.toTypedArray()
    }

    override fun save(out: DataOutput, array: Array<ByteArray>) {
      for (bytes in array) {
        JcHashExternalizer.save(out, bytes)
      }
    }
  }

  object JcHashExternalizer: KeyDescriptor<JcHash> {
    override fun getHashCode(value: JcHash): Int {
      var hash = 0 // take first 4 bytes, this should be good enough hash given we reference git revisions with 7-8 hex digits
      for (i in 0..3) {
        hash = (hash shl 8) + (value[i] and 0xFF.toByte())
      }
      return hash
    }

    override fun isEqual(val1: JcHash?, val2: JcHash?): Boolean {
      return Arrays.equals(val1, val2)
    }

    override fun read(`in`: DataInput): JcHash {
      val size = DataInputOutputUtil.readINT(`in`)
      val bytes = ByteArray(size)
      `in`.readFully(bytes)
      return bytes
    }

    override fun save(out: DataOutput, value: JcHash) {
      DataInputOutputUtil.writeINT(out, value.size)
      out.write(value)
    }
  }
}