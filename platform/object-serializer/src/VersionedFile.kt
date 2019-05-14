// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.util.ParameterizedTypeImpl
import com.intellij.util.io.move
import com.intellij.util.io.writeSafe
import java.io.IOException
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path

data class VersionedFile(val file: Path, val version: Int) {
  @Throws(IOException::class)
  @JvmOverloads
  fun <T> writeList(data: Collection<T>, itemClass: Class<T>, configuration: WriteConfiguration = defaultWriteConfiguration) {
    file.writeSafe { out ->
      ObjectSerializer.instance.serializer.writeVersioned(data, out.buffered(), version, originalType = ParameterizedTypeImpl(data.javaClass, itemClass), configuration = configuration)
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
    val result: T?
    try {
      result = ObjectSerializer.instance.serializer.readVersioned(objectClass, file, version, originalType = originalType, configuration = configuration)
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