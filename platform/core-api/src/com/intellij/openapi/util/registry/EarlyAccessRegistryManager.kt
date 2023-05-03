// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.util.registry

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Use only after consultation and approval - ask core team.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
object EarlyAccessRegistryManager {
  private val configFile: Path by lazy {
    PathManager.getConfigDir().resolve("early-access-registry.txt")
  }

  private val lazyMap = SynchronizedClearableLazy {
    val result = ConcurrentHashMap<String, String>()
    val lines = try {
      Files.lines(configFile)
    }
    catch (ignore: NoSuchFileException) {
      return@SynchronizedClearableLazy result
    }

    lines.use { lineStream ->
      val iterator = lineStream.iterator()
      while (iterator.hasNext()) {
        val key = iterator.next()
        if (!iterator.hasNext()) {
          break
        }
        result.put(key, iterator.next())
      }
    }
    result
  }

  private val map: ConcurrentHashMap<String, String>?
    get() {
      if (lazyMap.isInitialized()) {
        val map = lazyMap.value
        return if (map.isEmpty()) null else map
      }
      else {
        return null
      }
    }

  private val LOG: Logger
    get() = logger<EarlyAccessRegistryManager>()

  fun getBoolean(key: String): Boolean {
    if (key.isEmpty()) {
      LOG.error("Empty key")
      return false
    }

    val map = lazyMap.value
    if (!LoadingState.APP_STARTED.isOccurred) {
      return getOrFromSystemProperty(map, key)
    }

    // see com.intellij.ide.plugins.PluginDescriptorLoader.loadForCoreEnv
    val registryManager = ApplicationManager.getApplication().serviceOrNull<RegistryManager>() ?: return getOrFromSystemProperty(map, key)
    // use RegistryManager to make sure that Registry is fully loaded
    val value = registryManager.`is`(key)
    // ensure that even if for some reason key was not early accessed, it is stored for early access on next start-up
    map.putIfAbsent(key, value.toString())
    return value
  }

  private fun getOrFromSystemProperty(map: ConcurrentHashMap<String, String>, key: String): Boolean {
    return java.lang.Boolean.parseBoolean(map.get(key) ?: System.getProperty(key))
  }

  fun syncAndFlush() {
    // Why do we sync? get (not yet loaded) -> not changed by a user but actually in a registry -> no explicit put
    // Why maybe in a registry but not in our store?
    // Because store file deleted / removed / loaded from ICS or registry value was set before using EarlyAccessedRegistryManager
    val map = map ?: return
    val registryManager = ApplicationManager.getApplication().serviceIfCreated<RegistryManager>() ?: return
    try {
      val lines = mutableListOf<String>()
      for (key in map.keys.sorted()) {
        try {
          val value = registryManager.get(key).asString()
          lines.add(key)
          lines.add(value)
        }
        catch (ignore: MissingResourceException) {
        }
      }

      if (lines.isEmpty()) {
        Files.deleteIfExists(configFile)
      }
      else {
        Files.createDirectories(configFile.parent)
        Files.write(configFile, lines, StandardCharsets.UTF_8)
      }
    }
    catch (e: Throwable) {
      LOG.error("cannot save early access registry", e)
    }
  }

  fun invalidate() {
    check(!LoadingState.COMPONENTS_REGISTERED.isOccurred)
    lazyMap.drop()
  }

  @Suppress("unused") // registered in an `*.xml` file
  private class MyListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      val map = map ?: return

      // store only if presented - do not store alien keys
      val key = value.key
      if (map.containsKey(key)) {
        map.put(key, value.asString())
      }
    }
  }
}