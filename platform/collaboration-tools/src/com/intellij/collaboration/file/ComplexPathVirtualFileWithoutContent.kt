// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LeakingThis")

package com.intellij.collaboration.file

import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.vcs.editor.ComplexPathVirtualFileSystem

/**
 * [VirtualFile] of [ComplexPathVirtualFileSystem] that is supposed to be used a stub for opening various non-editor editors.
 *
 * [sessionId] is an identifier which is required to differentiate files between launches.
 * This is necessary to make the files appear in "Recent Files" correctly.
 * See [com.intellij.vcs.editor.ComplexPathVirtualFileSystem.ComplexPath.sessionId] for details.
 */
abstract class ComplexPathVirtualFileWithoutContent(protected val sessionId: String)
  : LightVirtualFileBase("", null, 0),
    VirtualFileWithoutContent,
    VirtualFilePathWrapper {

  init {
    putUserData(FileEditorManagerKeys.REOPEN_WINDOW, false)
    putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
    isWritable = false
  }

  override fun enforcePresentableName() = true

  abstract override fun getFileSystem(): ComplexPathVirtualFileSystem<*>
  override fun getFileType(): FileType = FileTypes.UNKNOWN

  override fun getLength() = 0L
  override fun contentsToByteArray() = throw UnsupportedOperationException()
  override fun getInputStream() = throw UnsupportedOperationException()
  override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = throw UnsupportedOperationException()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ComplexPathVirtualFileWithoutContent) return false

    return sessionId == other.sessionId
  }

  override fun hashCode(): Int {
    return sessionId.hashCode()
  }
}
