// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

private class FileTypeUsageServiceFileEditorManagerListener : FileEditorManagerListener {
  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    FileTypeUsageCounterCollector.triggerClosed(source.getProject(), file)
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    FileTypeUsageCounterCollector.triggerSelect(event.manager.getProject(), event.newFile)
  }
}
