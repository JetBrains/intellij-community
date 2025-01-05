// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.jps.state

import com.dynatrace.hash4j.hashing.HashFunnel
import com.dynatrace.hash4j.hashing.Hashing

internal inline fun <reified T : Enum<T>> hashFunnel(): HashFunnel<T> {
  return HashFunnel { obj, sink ->
    sink.putInt(obj.ordinal)
    sink.putString(obj.name)
  }
}

internal inline fun <reified T : Enum<T>> versionDigest(): Long {
  return Hashing.xxh3_64().hashStream().putOrderedIterable(enumValues<T>().asIterable(), hashFunnel<T>()).asLong
}