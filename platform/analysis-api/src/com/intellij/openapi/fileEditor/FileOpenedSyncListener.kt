// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

interface FileOpenedSyncListener {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC = Topic(FileOpenedSyncListener::class.java, Topic.BroadcastDirection.TO_PARENT, true)
  }

  /**
   * This method is called synchronously (in the same EDT event), as the creation of {@link FileEditor}s.
   */
  fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {}
}