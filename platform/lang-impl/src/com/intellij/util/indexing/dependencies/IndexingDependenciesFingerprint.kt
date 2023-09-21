// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.google.common.hash.HashCode
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

/**
 * This service collects all the indexing dependencies (e.g. plugins, project and app configuration, whatnot)
 * and builds a fingerprint of those dependencies. Fingerprint is a number. Change of the fingerprint means that
 * some files might be indexed differently now (provided that there are no VFS nor project model changes).
 *
 * In general, indexing depends on:
 * 1. Extensions. They are provided by plugins. No changes in enabled plugins means no changes in enabled extensions.
 * 1. Configuration. Extensions in plugin may depend on configuration. In the case of configuration change plugins must
 *    request (full) scanning (e.g. change in project language level). Full rescan counter also contributes to the fingerprint.
 *    It is not clear at the moment if partial rescans should contribute or not.
 * 1. VFS. VFS has its own means to track changed files. VFS is not in the scope of the fingerprint. // TODO
 * 1. Project model. TODO
 * 1. Shared indexes TODO
 * 1. JVM options and registry. They are kind of settings, but they do not trigger any rescanning - they usually require APP restart. TODO
 * 1. Did I miss something? Please contact me (e.g. by filing a YouTrack ticket).
 */

@ApiStatus.Internal
@Service(Service.Level.APP)
class IndexingDependenciesFingerprint {

  @ApiStatus.Internal
  data class FingerprintImpl(val fingerprint: HashCode)

  companion object {
    const val FINGERPRINT_SIZE_IN_BYTES = 32
    val NULL_FINGERPRINT = FingerprintImpl(HashCode.fromBytes(ByteArray(FINGERPRINT_SIZE_IN_BYTES)))
  }

  private val latestFingerprint = AtomicReference(NULL_FINGERPRINT)
  private var debugHelperToken: Int = 0

  private fun calculateFingerprint(): FingerprintImpl {
    val startTime = System.currentTimeMillis()
    val hasher = PluginHasher()
    PluginManager.getLoadedPlugins()
      .sortedBy { plugin -> plugin.pluginId.idString } // this will produce a copy of the list
      .forEach { plugin ->
        hasher.addPluginFingerprint(plugin)
      }

    hasher.mixInInt(debugHelperToken)
    val fingerprint = hasher.getFingerprint()
    val durationMs = System.currentTimeMillis() - startTime
    thisLogger().info("Calculated dependencies fingerprint in ${durationMs} ms ($fingerprint)")
    return FingerprintImpl(fingerprint)
  }

  fun getFingerprint(): FingerprintImpl {
    if (latestFingerprint.get() == NULL_FINGERPRINT) {
      latestFingerprint.compareAndSet(NULL_FINGERPRINT, calculateFingerprint())
    }
    return latestFingerprint.get()
  }

  fun resetCache() {
    latestFingerprint.set(NULL_FINGERPRINT)
  }

  @TestOnly
  fun changeFingerprintInTest() {
    debugHelperToken++
    latestFingerprint.set(NULL_FINGERPRINT)
  }
}