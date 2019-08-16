// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.io.move
import com.intellij.util.io.safeOutputStream
import net.jpountz.lz4.LZ4FrameInputStream
import net.jpountz.lz4.LZ4FrameOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Type
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private const val fileBufferSize = 32 * 1024
internal const val LZ4_MAGIC = 0x184D2204

private val versionedFileDefaultWriteConfiguration = defaultWriteConfiguration.copy(filter = SkipNullAndEmptySerializationFilter)

/**
 * [isCompressed] doesn't matter for read operation - all supported formats will be detected automatically.
 */
data class VersionedFile @JvmOverloads constructor(val file: Path, val version: Int, private val isCompressed: Boolean = true) {
  @Throws(IOException::class)
  @JvmOverloads
  fun <T> writeList(data: Collection<T>, itemClass: Class<T>, configuration: WriteConfiguration = versionedFileDefaultWriteConfiguration) {
    file.safeOutputStream().use {
      val out = if (isCompressed) LZ4FrameOutputStream(it, LZ4FrameOutputStream.BLOCKSIZE.SIZE_4MB) else it
      ObjectSerializer.instance.serializer.writeVersioned(data, out, version, configuration, ParameterizedTypeImpl(data.javaClass, itemClass))
    }
  }

  @Throws(IOException::class, SerializationException::class)
  @JvmOverloads
  @Suppress("UNCHECKED_CAST")
  fun <T> readList(itemClass: Class<T>, configuration: ReadConfiguration = ReadConfiguration(), renameToCorruptedOnError: Boolean = true): List<T>? =
    readAndHandleErrors(ArrayList::class.java, configuration, ParameterizedTypeImpl(ArrayList::class.java, itemClass), renameToCorruptedOnError) as List<T>?

  @Throws(IOException::class, SerializationException::class)
  @JvmOverloads
  fun <T : Any> read(objectClass: Class<T>, beanConstructed: BeanConstructed? = null): T? =
    readAndHandleErrors(objectClass, ReadConfiguration(beanConstructed = beanConstructed))

  private fun <T : Any> readAndHandleErrors(objectClass: Class<T>,
                                            configuration: ReadConfiguration,
                                            originalType: Type? = null,
                                            renameToCorruptedOnError: Boolean = true): T? =
    readPossiblyCompressedIonFile(file) { input ->
      val result = try {
        ObjectSerializer.instance.serializer.readVersioned(objectClass, input, file, version, configuration, originalType)
      }
      catch (e: Exception) {
        if (renameToCorruptedOnError) {
          renameSilentlyToCorrupted()
        }
        // in tests log will throw error, renameSilentlyToCorrupted is called before
        LOG.error(e)
        return null
      }
      if (result == null && renameToCorruptedOnError) {
        renameSilentlyToCorrupted()
      }
      return result
    }

  private fun renameSilentlyToCorrupted() {
    try {
      file.move(file.parent.resolve("${file.fileName}.corrupted"))
    }
    catch (e: Exception) {
      LOG.debug(e)
    }
  }
}

internal inline fun <T : Any> readPossiblyCompressedIonFile(file: Path, consumer: (InputStream) -> T?): T? {
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
    return consumer(input)
  }
}