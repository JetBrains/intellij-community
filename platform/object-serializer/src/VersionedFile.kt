// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serialization

import com.intellij.util.ParameterizedTypeImpl
import java.io.IOException
import java.nio.file.Path

data class VersionedFile(val file: Path, val version: Int) {
  @Throws(IOException::class)
  @JvmOverloads
  fun <T> writeList(data: Collection<T>, itemClass: Class<T>, configuration: WriteConfiguration = defaultWriteConfiguration) {
    ObjectSerializer.instance.serializer.writeVersioned(data, file, version, originalType = ParameterizedTypeImpl(data.javaClass, itemClass), configuration = configuration)
  }

  @Throws(IOException::class)
  @JvmOverloads
  fun <T> readList(itemClass: Class<T>, beanConstructed: BeanConstructed? = null): List<T>? {
    @Suppress("UNCHECKED_CAST")
    return ObjectSerializer.instance.serializer.readVersioned(ArrayList::class.java, file, version,
                                                              originalType = ParameterizedTypeImpl(ArrayList::class.java, itemClass),
                                                              configuration = ReadConfiguration(beanConstructed = beanConstructed)) as List<T>?
  }
}
