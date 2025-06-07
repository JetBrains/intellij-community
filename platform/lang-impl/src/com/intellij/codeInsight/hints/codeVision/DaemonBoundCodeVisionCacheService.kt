// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock

@Service(Service.Level.PROJECT)
internal class DaemonBoundCodeVisionCacheService {
  companion object {
    private val key = Key<FileCache>("daemon.bound.code.vision.cache")

    fun getInstance(project: Project): DaemonBoundCodeVisionCacheService = project.service<DaemonBoundCodeVisionCacheService>()
  }

  /**
   * Safe to call from different threads.
   *
   * @param providerId id of [DaemonBoundCodeVisionProvider], for which it is required to get lens data
   * @return computed (maybe not fresh) lens data for a given [editor] and for a [DaemonBoundCodeVisionProvider] with a given [providerId]
   */
  @RequiresReadLock
  fun getVisionDataForEditor(editor: Editor, providerId: String): CodeVisionWithStamp? {
    val cache = getFileCache(editor)
    return cache.get(providerId)
  }

  @RequiresWriteLock
  fun storeVisionDataForEditor(editor: Editor, providerId: String, data: CodeVisionWithStamp) {
    val fileCache = getFileCache(editor)
    fileCache.update(providerId, data)
  }

  private fun getFileCache(editor: Editor): FileCache {
    // needed to avoid races in cache creation
    return ConcurrencyUtil.computeIfAbsent(editor, key) { FileCache() }
  }

  // methods are protected with application RW lock (update under W, get under R)
  private class FileCache {
    private val providerIdToData: MutableMap<String, CodeVisionWithStamp> = HashMap()

    fun update(providerId: String, data: CodeVisionWithStamp) {
      providerIdToData[providerId] = data
    }

    fun get(providerId: String): CodeVisionWithStamp? {
      return providerIdToData[providerId]
    }
  }

  data class CodeVisionWithStamp(val codeVisionEntries: List<Pair<TextRange, CodeVisionEntry>>, val modificationStamp: Long)
}