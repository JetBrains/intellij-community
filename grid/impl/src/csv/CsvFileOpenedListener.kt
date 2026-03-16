// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.database.csv

import com.intellij.database.editor.CsvTableFileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeRegistry

internal class CsvFileOpenedListener : FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    val file = event.newFile ?: return
    if (!FileTypeRegistry.getInstance().isFileOfType(file, CsvFileType.INSTANCE)) {
      return
    }
    val selectedEditor = event.newEditor
    val mode = if (selectedEditor is CsvTableFileEditor) CsvFileUsageCollector.OpenMode.DATA else CsvFileUsageCollector.OpenMode.TEXT
    CsvFileUsageCollector.logFileOpened(event.manager.project, file, mode)
  }
}