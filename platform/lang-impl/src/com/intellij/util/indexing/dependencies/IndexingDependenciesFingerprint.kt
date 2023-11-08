// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.dependencies

import com.intellij.openapi.components.Service
import com.intellij.platform.ide.ideFingerprint
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
 * 1. VFS. VFS has its own means to track changed files. VFS is not in the scope of the fingerprint. We assume that each time VFS
 *    file changes, its `modificationCounter` changes as well. This counter is considered by [FileIndexingStamp], so changed file
 *    has modified file indexing stamp even though indexing dependencies fingerprint stays the same. We might include VFS creation stamp
 *    into the fingerprint, but we don't do it at the moment. Invalidation of VFS creation stamp is currently possible due to
 *    VFS re-create. In this case all the [FileIndexingStamp] are reset to default `UNINDEXED` state. (see "invalidate cache" notes
 *    for [com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService])
 * 1. Project model. Platform indexes tracks project model state by, and request full or partial scanning if project model changes.
 * 1. Shared indexes. Shared indexes track their state by themself, and should request full scanning if previously indexed files
 *    might become invalid. Specifically, on project open shared indexes tiy to re-attach all the chunks from previous session,
 *    and request full scanning if they could not attach some of them.
 *    See [com.intellij.indexing.shared.platform.impl.SharedIndexProjectActivity]
 * 1. JVM options and registry. They are kind of settings, but they do not trigger any rescanning - they usually require APP restart. TODO
 * 1. Did I miss something? Please contact me (e.g. by filing a YouTrack ticket).
 */

@ApiStatus.Internal
@Service(Service.Level.APP)
class IndexingDependenciesFingerprint {

  @ApiStatus.Internal
  data class FingerprintImpl(@JvmField val fingerprint: Long) {
    internal constructor(buffer: ByteBuffer) : this(fingerprint = buffer.rewind().order(ByteOrder.LITTLE_ENDIAN).getLong())

    fun toByteBuffer(): ByteBuffer {
      // still 32 bytes even though we need only 8 to avoid index storage invalidation
      val buffer = ByteBuffer.allocate(FINGERPRINT_SIZE_IN_BYTES).order(ByteOrder.LITTLE_ENDIAN)
      buffer.putLong(fingerprint)
      buffer.rewind()
      return buffer
    }
  }

  companion object {
    const val FINGERPRINT_SIZE_IN_BYTES: Int = 32
    val NULL_FINGERPRINT: FingerprintImpl = FingerprintImpl(0)
  }

  private val latestFingerprint = AtomicReference(NULL_FINGERPRINT)
  private var debugHelperToken: Int = 0

  private fun calculateFingerprint(): FingerprintImpl {
    //println(fingerprintString)
    //println(hasher.getDebugInfo())
    return FingerprintImpl(ideFingerprint(debugHelperToken))
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