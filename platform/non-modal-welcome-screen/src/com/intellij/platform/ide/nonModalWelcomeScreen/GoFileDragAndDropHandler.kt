// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Unmodifiable
import java.nio.file.Path

internal object GoFileDragAndDropHandler {

  fun openFiles(project: Project, files: @Unmodifiable List<Path>?): Boolean {
    if (files.isNullOrEmpty()) return false

    val fileEditorManager = FileEditorManager.getInstance(project)
    files.forEach { file ->
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
        readAction {
          LocalFileSystem.getInstance().findFileByNioFile(file)
        }?.let {
          withContext(Dispatchers.EDT) {
            fileEditorManager.openFile(it, true)
          }
        }
      }
    }
    return true
  }
}
