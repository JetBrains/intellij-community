// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore

import org.h2.mvstore.MVMap
import java.io.File
import java.nio.file.Path

interface MvStoreMapFactory {
  fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, V>): MVMap<K, V>

  fun getStringEnumerator(): StringEnumerator

  fun getOldPathRelativizer(): LegacyKotlinPathRelativizer

  interface LegacyKotlinPathRelativizer {
    fun toRelative(file: File): String

    fun toAbsoluteFile(path: String): File
  }
}

val mvStoreMapFactoryExposer: ThreadLocal<MvStoreMapFactory> = ThreadLocal<MvStoreMapFactory>()