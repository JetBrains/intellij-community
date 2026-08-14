// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
const val ANALYSIS_IGNORE_FILE_NAME: String = ".analysisignore"

internal const val ANALYSIS_IGNORE_ENABLED_KEY: String = "ide.analysisignore.file.enabled"

private val analysisIgnoreFileNameId: Int = VirtualFileManager.getInstance().storeName(ANALYSIS_IGNORE_FILE_NAME)


internal fun VirtualFile.isAnalysisIgnoreFile(): Boolean {
  return if (this is VirtualFileSystemEntry) nameId == analysisIgnoreFileNameId else name == ANALYSIS_IGNORE_FILE_NAME
}

/**
 * Returns `true` if [url] is the URL of the directory at [dirUrl] or of a file below it.
 */
internal fun isUnderOrEqual(url: String, dirUrl: String): Boolean = FileUtil.startsWith(url, dirUrl)
