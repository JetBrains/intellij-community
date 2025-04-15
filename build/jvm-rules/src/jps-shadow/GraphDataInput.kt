// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency

import org.jetbrains.bazel.jvm.util.emptyList
import java.io.DataInput
import java.io.IOException

interface GraphDataInput : DataInput {
  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement> readGraphElement(): T

  @Throws(IOException::class)
  fun <T : ExternalizableGraphElement, C : MutableCollection<in T>> readGraphElementCollection(result: C): C

  fun readRawLong(): Long

  fun readStringList(): List<String> {
    return readList { readUTF() }
  }

  override fun readLine(): String = throw UnsupportedOperationException()
}

inline fun <reified T : Any> GraphDataInput.readList(reader: GraphDataInput.() -> T): List<T> {
  val size = readInt()
  if (size == 0) {
    return emptyList()
  }

  return Array(size) {
    reader()
  }.asList()
}