// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.file.codereview

import com.intellij.diff.editor.DiffFileType
import com.intellij.diff.editor.DiffViewerVirtualFile
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
abstract class CodeReviewDiffVirtualFile(sourceId: String)
  : DiffViewerVirtualFile(sourceId),
    VirtualFilePathWrapper {

  init {
    @Suppress("LeakingThis")
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
  }

  abstract override fun getPresentableName(): String

  abstract override fun getPath(): String
  abstract override fun getPresentablePath(): String

  final override fun enforcePresentableName() = true

  abstract override fun isValid(): Boolean

  abstract override fun getFileSystem(): ComplexPathVirtualFileSystem<*>
  final override fun getFileType(): FileType = DiffFileType.INSTANCE

  final override fun getLength(): Long = 0L
  final override fun contentsToByteArray(): Nothing = throw UnsupportedOperationException()
  final override fun getInputStream(): Nothing = throw UnsupportedOperationException()
  final override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): Nothing =
    throw UnsupportedOperationException()
}