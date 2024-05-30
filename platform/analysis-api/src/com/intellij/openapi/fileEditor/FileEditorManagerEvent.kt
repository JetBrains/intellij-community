// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

class FileEditorManagerEvent @ApiStatus.ScheduledForRemoval @Deprecated("Use constructor accepting {@link FileEditorWithProvider}") constructor(
  val manager: FileEditorManager,
  val oldFile: VirtualFile?,
  val oldEditor: FileEditor?,
  val oldProvider: FileEditorProvider?,
  val newFile: VirtualFile?,
  val newEditor: FileEditor?,
  val newProvider: FileEditorProvider?,
) {
  constructor(
    manager: FileEditorManager,
    oldEditorWithProvider: FileEditorWithProvider?,
    newEditorWithProvider: FileEditorWithProvider?,
  ) : this(
    manager = manager,
    oldFile = oldEditorWithProvider?.fileEditor?.file,
    oldEditor = oldEditorWithProvider?.fileEditor,
    oldProvider = oldEditorWithProvider?.provider,
    newFile = newEditorWithProvider?.fileEditor?.file,
    newEditor = newEditorWithProvider?.fileEditor,
    newProvider = newEditorWithProvider?.provider,
  )
}