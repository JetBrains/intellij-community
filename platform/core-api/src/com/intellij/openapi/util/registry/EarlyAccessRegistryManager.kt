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
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager.fileName
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val LOG: Logger
  get() = logger<EarlyAccessRegistryManager>()

private val configFile: Path by lazy {
  PathManager.getOriginalConfigDir().resolve(EarlyAccessRegistryManager.fileName)
}

private const val DISABLE_SAVE_PROPERTY = "early.access.registry.disable.saving"

private val lazyMap = SynchronizedClearableLazy {
  val result = ConcurrentHashMap<String, String>()
  val lines = try {
    Files.lines(configFile)
  }
  catch (_: NoSuchFileException) {
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
 * Obsolete. Avoid using in new code.
 *
 * `EarlyAccessRegistryManager` exists **only** for a historical, one-off case:
 * letting end users toggle the *New UI* flag via the Registry UI **before**
 * the full configuration store was initialized.
 *
 * Why you should not use it:
 * - Startup must not be blocked by loading configs. Reading “real” values early
 *   is expensive and couples startup to configuration store initialization.
 * - It creates hidden state that later has to be reconciled with the actual
 *   `Registry` and persisted settings, which is fragile and error-prone.
 *
 * What to use instead:
 * 1) **System property** (preferred for low-level or risky switches).
 *    - Define a property key, read it early with a safe default.
 *    - Document it in dev notes and feature flags list.
 *    - Example key: `idea.use.new.file.system`.
 *
 * 2) **Defer & re-apply** when configs become available.
 *    - Start with a safe default.
 *    - When `Registry`/settings are loaded, re-read the real value and reapply
 *      (reinitialize/refresh) if needed. Startup stays decoupled from config I/O.
 *
 * Decision guide:
 * - Does the flag affect very low-level behavior (runs before configuration store initialization)?
 *   → Use a **system property** (no UI).
 * - Do you only need the setting after the app is up?
 *   → Read from **Registry** or settings normally; don’t touch early paths.
 * - Do you think you must read the *real* user/IDE setting during early startup?
 *   → **Don’t.** Use a temporary default and **re-apply later**. If that’s
 *   impossible, consult the core team with a concrete justification.
 *
 * If you believe you still need this class:
 * - Please consult the core team. New usages are strongly discouraged.
 *
 * This class remains only for compatibility with the historical *New UI* rollout.
 */
@Obsolete
@Internal
object EarlyAccessRegistryManager {
  @Suppress("ConstPropertyName")
  const val fileName: String = "early-access-registry.txt"

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
      return getOrFromSystemProperty(map, key)?.takeIf { it.isNotEmpty() }
    }

    // see com.intellij.ide.plugins.PluginDescriptorLoader.loadForCoreEnv
    val registryManager = ApplicationManager.getApplication()?.serviceOrNull<RegistryManager>() ?: return getOrFromSystemProperty(map, key)
    // use RegistryManager to make sure that Registry is fully loaded
    val value = try {
      registryManager.stringValue(key)
    }
    catch (_: MissingResourceException) {
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
    lazyMap.value.put(key, value.toString())
    ApplicationManager.getApplication().serviceIfCreated<RegistryManager>()?.get(key)?.setValue(value)
  }

  /**
   * Updates value for registry property which may be accessed via this class.
   * Use this function instead of the default [RegistryValue.setValue] to ensure that the updated value will be saved to [fileName].
   */
  fun setString(key: String, value: String) {
    lazyMap.value.put(key, value)
    ApplicationManager.getApplication().serviceIfCreated<RegistryManager>()?.get(key)?.setValue(value)
  }
  
  fun syncAndFlush() {
    // Why do we sync? get (not yet loaded) -> not changed by a user but actually in a registry -> no explicit put
    // Why maybe in a registry but not in our store?
    // Because store file deleted / removed / loaded from ICS or registry value was set before using EarlyAccessedRegistryManager
    val map = map ?: return
    val registryManager = serviceIfCreated<RegistryManager>() ?: return
    try {
      saveConfigFile(map, configFile) {
        try {
          registryManager.stringValue(it)
        }
        catch (_: MissingResourceException) {
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

private inline fun saveConfigFile(
  map: ConcurrentHashMap<String, String>,
  @Suppress("SameParameterValue") configFile: Path,
  provider: (String) -> String?,
) {
  if (System.getProperty(DISABLE_SAVE_PROPERTY) == "true") {
    return
  }

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