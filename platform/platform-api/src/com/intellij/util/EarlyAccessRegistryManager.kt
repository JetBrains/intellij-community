// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.util

import com.intellij.application.options.RegistryManager
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import org.jetbrains.annotations.ApiStatus
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

  private val map = lazy {
    val result = ConcurrentHashMap<String, String>()
    val lines = try {
      Files.lines(configFile)
    }
    catch (ignore: NoSuchFileException) {
      return@lazy result
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

  fun getBoolean(key: String): Boolean {
    if (!LoadingState.APP_STARTED.isOccurred) {
      return map.value.get(key).toBoolean()
    }

    // use RegistryManager to make sure that Registry is a fully loaded
    val value = RegistryManager.getInstance().`is`(key)
    // ensure that even if for some reason key was not early accessed, it is stored for early access on next start-up
    map.value.putIfAbsent(key, value.toString())
    return value
  }

  fun syncAndFlush() {
    // Why do we sync? get (not yet loaded) -> not changed by a user but actually in a registry -> no explicit put
    // Why maybe in a registry but not in our store?
    // Because store file deleted / removed / loaded from ICS or registry value was set before using EarlyAccessedRegistryManager
    if (!map.isInitialized() || map.value.isEmpty()) {
      return
    }

    val registryManager = ApplicationManager.getApplication().getServiceIfCreated(RegistryManager::class.java) ?: return
    try {
      val s = StringBuilder()
      for (key in map.value.keys.sorted()) {
        val value = try {
          registryManager.get(key).asString()
        }
        catch (ignore: MissingResourceException) {
          continue
        }

        s.append(key).append('\n').append(value).append('\n')
      }

      if (s.isEmpty()) {
        Files.deleteIfExists(configFile)
      }
      else {
        s.setLength(s.length - 1)

        Files.createDirectories(configFile.parent)
        Files.writeString(configFile, s)
      }
    }
    catch (e: Throwable) {
      Logger.getInstance(EarlyAccessRegistryManager::class.java).error("cannot save early access registry", e)
    }
  }

  @Suppress("unused")
  private class MyListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      // store only if presented - do not store alien keys
      val key = value.key
      val map = if (map.isInitialized()) map.value else return
      if (map.containsKey(key)) {
        map.put(key, value.asString())
      }
    }
  }
}