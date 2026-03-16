// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl

import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

interface CachedDescriptorReader {
  /**
   * Uncommited changes are not visible (i.e. [DescriptorCacheWriter.apply] not yet called).
   */
  fun getCachedFileData(name: String): ByteArray?
}

interface ScopedCachedDescriptorContainer : CachedDescriptorWriterProvider, CachedDescriptorReader {
  val isModuleSetOwner: Boolean
}

private sealed interface CachedDescriptorContainerKey

private data class PluginKey(@JvmField val pluginDir: Path) : CachedDescriptorContainerKey {
  init {
    require(!pluginDir.endsWith("lib")) {
      "Plugin target directory cannot end with 'lib'"
    }
  }
}

private data class PlatformKey(@JvmField val platformLayout: PlatformLayout) : CachedDescriptorContainerKey

const val PRODUCT_DESCRIPTOR_META_PATH: String = "__product-descriptor__.xml"

class DescriptorCacheContainer internal constructor() {
  private var containerToContent = ConcurrentHashMap<CachedDescriptorContainerKey, Map<String, ByteArray>>(200)

  fun forPlatform(platformLayout: PlatformLayout): ScopedCachedDescriptorContainer {
    return ScopedCachedDescriptorContainerImpl(PlatformKey(platformLayout), containerToContent, isModuleSetOwner = true)
  }

  /**
   * We use pluginTargetDir because it contains OS, as we maybe have separate variants of plugin (so, we cannot use main module name as a key).
   */
  fun forPlugin(pluginDir: Path): ScopedCachedDescriptorContainer {
    return ScopedCachedDescriptorContainerImpl(PluginKey(pluginDir), containerToContent, isModuleSetOwner = false)
  }

  override fun toString(): String {
    val info = containerToContent.entries.associate { (path, files) ->
      path to files.keys.toList()
    }

    val sb = StringBuilder("CachedDescriptorContainer(\n")
    sb.append("  dirs={\n")
    for ((path, files) in info) {
      sb.append("    $path=$files,\n")
    }
    sb.append("  },\n")
    sb.append(")")

    return sb.toString()
  }
}

private data class ScopedCachedDescriptorContainerImpl(
  private val key: CachedDescriptorContainerKey,
  private val containerToContent: ConcurrentHashMap<CachedDescriptorContainerKey, Map<String, ByteArray>>,
  override val isModuleSetOwner: Boolean,
) : ScopedCachedDescriptorContainer {
  override fun getCachedFileData(name: String): ByteArray? {
    return containerToContent.get(key)?.get(name)
  }

  override fun putIfAbsent(name: String, data: ByteArray) {
    containerToContent.compute(key) { _, oldValue ->
      when {
        oldValue == null -> java.util.Map.of(name, data)
        oldValue.containsKey(name) -> oldValue
        else -> {
          val newMap = HashMap<String, ByteArray>(oldValue.size + 1)
          newMap.putAll(oldValue)
          newMap.put(name, data)
          newMap
        }
      }
    }
  }

  override fun put(name: String, data: ByteArray) {
    containerToContent.compute(key) { _, oldValue ->
      when {
        oldValue == null -> java.util.Map.of(name, data)
        else -> {
          val newMap = HashMap<String, ByteArray>(oldValue.size + 1)
          newMap.putAll(oldValue)
          newMap.put(name, data)
          newMap
        }
      }
    }
  }

  override fun write(): DescriptorCacheWriter {
    return object : DescriptorCacheWriter {
      private var map = HashMap<String, ByteArray>()

      @Synchronized
      override fun put(name: String, data: ByteArray) {
        map.put(name, data)
      }

      @Synchronized
      override fun apply() {
        if (map.isEmpty()) {
          return
        }

        containerToContent.merge(key, map) { oldValue, newValue ->
          val newMap = HashMap<String, ByteArray>(oldValue.size + newValue.size)
          newMap.putAll(oldValue)
          newMap.putAll(newValue)
          newMap
        }
      }
    }
  }
}

interface DescriptorCacheWriter {
  fun put(name: String, data: ByteArray)

  fun apply()
}

interface CachedDescriptorWriterProvider {
  fun write(): DescriptorCacheWriter

  fun put(name: String, data: ByteArray)

  fun putIfAbsent(name: String, data: ByteArray)
}