// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import kotlinx.collections.immutable.persistentHashMapOf
import org.jdom.Element

class CachedDescriptorContainer internal constructor() {
  private var cachedXmlFiles = persistentHashMapOf<String, ByteArray>()

  @get:Synchronized
  @set:Synchronized
  @JvmField
  internal var productDescriptor: Element? = null

  fun getCachedFileData(name: String): ByteArray? = cachedXmlFiles.get(name)

  fun mutate(): Mutator {
    return object : Mutator {
      private var map = persistentHashMapOf<String, ByteArray>()

      @Synchronized
      override fun put(name: String, data: ByteArray) {
        map = map.put(name, data)
      }

      @Synchronized
      override fun apply() {
        if (map.isNotEmpty()) {
          synchronized(cachedXmlFiles) {
            cachedXmlFiles = cachedXmlFiles.putAll(map)
          }
        }
      }
    }
  }

  interface Mutator {
    fun put(name: String, data: ByteArray)

    fun apply()
  }
}