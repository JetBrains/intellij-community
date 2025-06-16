// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.jps.dependency.storage

import androidx.collection.IntObjectMap
import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableObjectIntMap
import androidx.collection.ObjectIntMap

private val predefinedStringArray = arrayOf(
  "",
  "<init>",
  "Ljava/lang/String;",
  "Ljava/lang/CharSequence;",
  "Ljava/lang/Object;",
  "Ljava/awt/datatransfer/DataFlavor;",
  "Ljava/util/List;",
  "Lkotlin/coroutines/Continuation;",
  "Ljavax/swing/JComponent;",
  "Ljava/nio/ByteBuffer;",
  "Ljava/io/InputStream;",
  "java/io/IOException",
  "Ljava/net/URL;",
  "java/lang/Deprecated;",
  "org/jetbrains/annotations/Nullable",
  "org/jetbrains/annotations/NotNull",
  "V",
  "I",
  "Z",
  "[B",
  "<T:Ljava/lang/Object;>Ljava/lang/Object;",
  "kotlin",
  "kotlin/Boolean",
  "kotlin/Int",
  "kotlin/Long",
  "kotlin/ByteArray",
  "kotlin/String",
  "kotlin/Deprecated",
)

internal val predefinedStrings = ArrayList<String>(predefinedStringArray.asList())

fun createStringMap(): MutableObjectIntMap<String> {
  val result = MutableObjectIntMap<String>(predefinedStringArray.size * 2)
  for ((i, s) in predefinedStringArray.withIndex()) {
    result.set(s, i)
  }
  return result
}

fun createImmutableStringMap(): ObjectIntMap<String> {
  val result = MutableObjectIntMap<String>(predefinedStringArray.size)
  for ((i, s) in predefinedStringArray.withIndex()) {
    result.set(s, i)
  }
  return result
}

fun createImmutableIndexToStringMap(): IntObjectMap<String> {
  val result = MutableIntObjectMap<String>(predefinedStringArray.size)
  for ((i, s) in predefinedStringArray.withIndex()) {
    result.set(i, s)
  }
  return result
}