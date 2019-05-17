// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.serialization.*
import net.jpountz.lz4.LZ4CompressorWithLength
import net.jpountz.lz4.LZ4DecompressorWithLength
import net.jpountz.lz4.LZ4Factory
import java.io.OutputStream

val externalSystemBeanConstructed: BeanConstructed = {
  if (it is ProjectSystemId) {
    it.intern()
  }
  else {
    it
  }
}

fun createDataNodeReadConfiguration(classLoader: ClassLoader): ReadConfiguration {
  return ReadConfiguration(allowAnySubTypes = true, classLoader = classLoader, beanConstructed = externalSystemBeanConstructed)
}

fun <T : Any> readDataNodeData(dataClass: Class<T>, data: ByteArray, classLoader: ClassLoader): T {
  val decompressor = LZ4DecompressorWithLength(LZ4Factory.fastestInstance().fastDecompressor())
  return ObjectSerializer.instance.read(dataClass, decompressor.decompress(data), createDataNodeReadConfiguration(classLoader))
}

fun serializeDataNodeData(data: Any, buffer: WriteAndCompressSession): ByteArray {
  ObjectSerializer.instance.write(data, buffer.resetAndGetOutputStream(), WriteConfiguration(allowAnySubTypes = true, filter = SkipNullAndEmptySerializationFilter))
  return buffer.compress()
}

/**
 * To write and compress a lot of elements sequentially into separate byte arrays.
 * Reuses input and output byte arrays.
 */
class WriteAndCompressSession {
  private val compressor = LZ4CompressorWithLength(LZ4Factory.fastestInstance().fastCompressor())
  private val buffer = BufferExposingByteArrayOutputStream()

  private var lastByteArray: ByteArray? = null

  fun compress(): ByteArray {
    val maxCompressedLength = compressor.maxCompressedLength(buffer.size())

    var compressed = lastByteArray
    if (compressed == null || compressed.size < maxCompressedLength) {
      compressed = ByteArray(maxCompressedLength)
    }

    val compressedLength = compressor.compress(buffer.internalBuffer, 0, buffer.size(), compressed, 0, maxCompressedLength)
    if (compressedLength == compressed.size) {
      lastByteArray = null
      return compressed
    }
    else {
      lastByteArray = compressed
      return compressed.copyOf(compressedLength)
    }
  }

  fun resetAndGetOutputStream(): OutputStream {
    buffer.reset()
    return buffer
  }
}