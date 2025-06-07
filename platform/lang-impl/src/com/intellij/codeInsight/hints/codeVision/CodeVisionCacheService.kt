// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.util.ConcurrencyUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * This service caches code vision entries in the editor userdata.
 * For the daemon bound version, see [DaemonBoundCodeVisionCacheService].
 */
@Service(Service.Level.PROJECT)
internal class CodeVisionCacheService {
  companion object {
    private val key = Key<ConcurrentHashMap<String, CodeVisionWithStamp>>("code.vision.cache")
  }

  private fun getOrCreateCache(editor: Editor) = ConcurrencyUtil.computeIfAbsent(editor, key) { ConcurrentHashMap() }

  fun getVisionDataForEditor(editor: Editor, providerId: String): CodeVisionWithStamp? {
    val cache = getOrCreateCache(editor)
    return cache[providerId]
  }

  fun storeVisionDataForEditor(editor: Editor, providerId: String, data: CodeVisionWithStamp) {
    val fileCache = getOrCreateCache(editor)
    fileCache[providerId] = data
  }

  data class CodeVisionWithStamp(val codeVisionEntries: List<Pair<TextRange, CodeVisionEntry>>, val modificationStamp: Long)
}