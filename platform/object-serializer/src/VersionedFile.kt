// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.io.move
import com.intellij.util.io.writeSafe
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.lz4.LZ4FrameOutputStream
import java.io.IOException
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private const val fileBufferSize = 32 * 1024
private const val LZ4_MAGIC = 0x184D2204
/**
 * [isCompressed] doesn't matter for read operation - all supported formats will be detected automatically.
 */
data class VersionedFile @JvmOverloads constructor(val file: Path, val version: Int, private val isCompressed: Boolean = false) {
  @Throws(IOException::class)
  @JvmOverloads
  fun <T> writeList(data: Collection<T>, itemClass: Class<T>, configuration: WriteConfiguration = defaultWriteConfiguration) {
    file.writeSafe { fileOut ->
      val out = when {
        isCompressed -> LZ4FrameOutputStream(fileOut, LZ4FrameOutputStream.BLOCKSIZE.SIZE_4MB)
        else -> fileOut.buffered(fileBufferSize)
      }

      ObjectSerializer.instance.serializer.writeVersioned(data, out, version, originalType = ParameterizedTypeImpl(data.javaClass, itemClass), configuration = configuration)
    }
  }

  @Throws(IOException::class, SerializationException::class)
  @JvmOverloads
  fun <T> readList(itemClass: Class<T>, beanConstructed: BeanConstructed? = null): List<T>? {
    val configuration = ReadConfiguration(beanConstructed = beanConstructed)
    @Suppress("UNCHECKED_CAST")
    return readAndHandleErrors(ArrayList::class.java, configuration, originalType = ParameterizedTypeImpl(ArrayList::class.java, itemClass)) as List<T>?
  }

  @Throws(IOException::class, SerializationException::class)
  @JvmOverloads
  fun <T : Any> read(objectClass: Class<T>, beanConstructed: BeanConstructed? = null): T? {
    return readAndHandleErrors(objectClass, ReadConfiguration(beanConstructed = beanConstructed))
  }

  private fun <T : Any> readAndHandleErrors(objectClass: Class<T>, configuration: ReadConfiguration, originalType: Type? = null): T? {
    val channel = try {
      Files.newByteChannel(file, setOf(StandardOpenOption.READ))
    }
    catch (e: NoSuchFileException) {
      return null
    }
    catch (e: IOException) {
      LOG.error(e)
      return null
    }

    channel.use {
      val lz4Magic = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
      channel.read(lz4Magic)
      channel.position(0)
      var input = Channels.newInputStream(channel)
      input = when (lz4Magic.getInt(0)) {
        LZ4_MAGIC -> LZ4FrameInputStream(input)
        else -> input.buffered(fileBufferSize)
      }

      val result: T?
      try {
        result = ObjectSerializer.instance.serializer.readVersioned(objectClass, input, file, version, originalType = originalType,
                                                                    configuration = configuration)
      }
      catch (e: Exception) {
        try {
          file.move(file.parent.resolve("${file.fileName}.corrupted"))
        }
        catch (ignore: Exception) {
        }

        LOG.error(e)
        return null
      }

      if (result == null) {
        try {
          Files.delete(file)
        }
        catch (ignore: IOException) {
        }
        return null
      }

      return result
    }
  }
}