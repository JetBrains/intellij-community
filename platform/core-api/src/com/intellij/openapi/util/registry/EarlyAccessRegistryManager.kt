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
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val LOG: Logger
  get() = logger<EarlyAccessRegistryManager>()

private val configFile: Path by lazy {
  PathManager.getConfigDir().resolve(EarlyAccessRegistryManager.fileName)
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

/**
 * Provides a configuration of internal settings which might be used before application has been loaded unless [Registry].
 *
 * Please avoid to use it, consult with someone from core team first.
 */
@ApiStatus.Internal
object EarlyAccessRegistryManager {
  @Suppress("ConstPropertyName")
  const val fileName: String = "early-access-registry.txt"
  const val DISABLE_SAVE_PROPERTY = "early.access.registry.disable.saving"

  fun getBoolean(key: String): Boolean {
    return getString(key).toBoolean()
  }

  fun getString(key: String): String? {
    if (key.isEmpty()) {
      LOG.error("Empty key")
      return null
    }

    val map = lazyMap.value
    if (!LoadingState.APP_STARTED.isOccurred) {
      return getOrFromSystemProperty(map, key).nullize()
    }

    // see com.intellij.ide.plugins.PluginDescriptorLoader.loadForCoreEnv
    val registryManager = ApplicationManager.getApplication()?.serviceOrNull<RegistryManager>() ?: return getOrFromSystemProperty(map, key)
    // use RegistryManager to make sure that Registry is fully loaded
    val value = try {
      registryManager.stringValue(key)
    }
    catch (ignore: MissingResourceException) {
      null
    }

    if (value == null) {
      return null
    }

    // ensure that even if key was not early accessed for some reason, it is stored for early access on next start-up
    map.putIfAbsent(key, value)
    return value.takeIf { it.isNotEmpty() }
  }

  fun getOrLoadMap(): Map<String, String> = lazyMap.value

  fun setAndFlush(data: Map<String, String>) {
    check(!LoadingState.COMPONENTS_REGISTERED.isOccurred)
    val map = lazyMap.value
    map.putAll(data)
    saveConfigFile(map, configFile) { map.get(it) }
  }

  /**
   * Updates value for registry property which may be accessed via this class. 
   * Use this function instead of the default [RegistryValue.setValue] to ensure that the updated value will be saved to [fileName]. 
   */
  fun setBoolean(key: String, value: Boolean) {
    lazyMap.value[key] = value.toString()
    ApplicationManager.getApplication().serviceIfCreated<RegistryManager>()?.get(key)?.setValue(value)
  }

  /**
   * Updates value for registry property which may be accessed via this class.
   * Use this function instead of the default [RegistryValue.setValue] to ensure that the updated value will be saved to [fileName].
   */
  fun setString(key: String, value: String) {
    lazyMap.value[key] = value
    ApplicationManager.getApplication().serviceIfCreated<RegistryManager>()?.get(key)?.setValue(value)
  }
  
  fun syncAndFlush() {
    // Why do we sync? get (not yet loaded) -> not changed by a user but actually in a registry -> no explicit put
    // Why maybe in a registry but not in our store?
    // Because store file deleted / removed / loaded from ICS or registry value was set before using EarlyAccessedRegistryManager
    val map = map ?: return
    val registryManager = ApplicationManager.getApplication().serviceIfCreated<RegistryManager>() ?: return
    try {
      saveConfigFile(map, configFile) {
        try {
          registryManager.stringValue(it)
        }
        catch (ignore: MissingResourceException) {
          null
        }
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
}

private class EarlyAccessRegistryManagerListener : RegistryValueListener {
  override fun afterValueChanged(value: RegistryValue) {
    val map = map ?: return

    // store only if presented - do not store alien keys
    val key = value.key
    if (map.containsKey(key)) {
      map.put(key, value.asString())
    }
  }
}

private fun getOrFromSystemProperty(map: ConcurrentHashMap<String, String>, key: String): String? {
  return map.get(key) ?: System.getProperty(key)
}

private inline fun saveConfigFile(map: ConcurrentHashMap<String, String>,
                                  @Suppress("SameParameterValue") configFile: Path,
                                  provider: (String) -> String?) {
  if (System.getProperty(EarlyAccessRegistryManager.DISABLE_SAVE_PROPERTY) == "true")
    return
  val lines = mutableListOf<String>()
  for (key in map.keys.sorted()) {
    val value = provider(key) ?: continue
    lines.add(key)
    lines.add(value)
  }

  if (lines.isEmpty()) {
    Files.deleteIfExists(configFile)
  }
  else {
    Files.createDirectories(configFile.parent)
    Files.write(configFile, lines, StandardCharsets.UTF_8)
  }
}