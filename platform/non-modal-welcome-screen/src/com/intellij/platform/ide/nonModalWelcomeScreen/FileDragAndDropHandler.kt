// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.ide.TrustedFiles
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.projectImport.ProjectOpenProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Unmodifiable
import java.nio.file.Path

interface FileDragAndDropHandler {
  fun openFiles(project: Project, files: @Unmodifiable List<Path>?): Boolean
}

/**
 * Opens a dropped file as a project when a [ProjectOpenProcessor] can open it, for example a project directory or a solution file.
 * The new project replaces the welcome screen project.
 * Opens all other dropped files in the editor.
 */
object DefaultFileDragAndDropHandler : FileDragAndDropHandler {

  override fun openFiles(project: Project, files: @Unmodifiable List<Path>?): Boolean {
    if (files.isNullOrEmpty()) return false

    val fileEditorManager = FileEditorManager.getInstance(project)
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      val regularFiles = mutableListOf<VirtualFile>()
      for (file in files) {
        val virtualFile = readAction { VirtualFileManager.getInstance().findFileByNioPath(file) } ?: continue
        val canOpenAsProject = readAction { ProjectOpenProcessor.getImportProvider(virtualFile) != null }
        if (canOpenAsProject) {
          ProjectUtil.openOrImportAsync(file, OpenProjectTask { projectToClose = project })
        }
        else {
          regularFiles.add(virtualFile)
        }
      }
      for (virtualFile in regularFiles) {
        NonProjectFileWritingAccessProvider.allowWriting(listOf(virtualFile))
        TrustedFiles.markExternallyOpened(virtualFile)
        withContext(Dispatchers.EDT) {
          fileEditorManager.openFile(virtualFile, true)
        }
      }
    }
    return true
  }
}
