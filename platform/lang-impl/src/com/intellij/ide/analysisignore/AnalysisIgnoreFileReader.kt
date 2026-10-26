// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import java.io.IOException

internal data class AnalysisIgnoreRecord(
  val baseDir: VirtualFileUrl,
  val patterns: List<String>,
)

internal fun readAnalysisIgnoreRecord(file: VirtualFile, urlManager: VirtualFileUrlManager): AnalysisIgnoreRecord? {
  if (!file.isValid || file.isDirectory) return null
  val baseDir = file.parent ?: return null

  val text = readTextOrNull(file)
  val patterns = if (text == null) emptyList() else supportedPatternsOf(file, text)

  val cacheableBaseDir = (baseDir as? CacheAvoidingVirtualFile)?.asCacheable() ?: baseDir
  return AnalysisIgnoreRecord(
    baseDir = cacheableBaseDir.toVirtualFileUrl(urlManager),
    patterns = patterns,
  )
}

/**
 * Returns the patterns of [text] that the format supports, in the order of the file. Names each pattern that the format refuses in the log.
 */
private fun supportedPatternsOf(file: VirtualFile, text: CharSequence): List<String> {
  val patterns = ArrayList<String>()

  var lineNumber = 0
  for (rawLine in text.lineSequence()) {
    lineNumber++

    val source = AnalysisIgnorePattern.patternSource(rawLine) ?: continue
    when (val validated = AnalysisIgnorePattern.validate(source)) {
      AnalysisIgnoreValidated.Supported -> patterns.add(source)
      is AnalysisIgnoreValidated.Unsupported ->
        LOG.warn("$ANALYSIS_IGNORE_FILE_NAME does not support ${validated.reason.message}: " +
                 "'$source' at ${file.presentableUrl}:$lineNumber")
    }
  }

  return patterns
}

private fun readTextOrNull(file: VirtualFile): CharSequence? {
  val cachedFile = when {
    file !is CacheAvoidingVirtualFile -> file
    file.isCached -> file.asCacheable()
    else -> null
  }
  if (cachedFile != null) {
    FileDocumentManager.getInstance().getCachedDocument(cachedFile)?.let { return it.immutableCharSequence }
  }

  val fileToRead = cachedFile ?: file
  return try {
    VfsUtilCore.loadText(fileToRead)
  }
  catch (e: IOException) {
    LOG.warn("Cannot read ${fileToRead.presentableUrl}", e)
    null
  }
}

private val LOG = logger<AnalysisIgnoreRecord>()
