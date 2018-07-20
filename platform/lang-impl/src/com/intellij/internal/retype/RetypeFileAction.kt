// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.retype

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

/**
 * @author yole
 */
class RetypeFileAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl
    val project = e.getData(CommonDataKeys.PROJECT) ?: return
    val existingSession = editor?.getUserData(RETYPE_SESSION_KEY)
    if (existingSession != null) {
      existingSession.stop(false)
    }
    else {
      val retypeOptionsDialog = RetypeOptionsDialog(project, editor != null)
      if (!retypeOptionsDialog.showAndGet()) return
      if (retypeOptionsDialog.isRetypeCurrentFile) {
        val session = RetypeSession(project, editor!!, retypeOptionsDialog.retypeDelay, retypeOptionsDialog.threadDumpDelay)
        session.start()
      }
      else {
        val queue = RetypeQueue(project, retypeOptionsDialog.retypeDelay, retypeOptionsDialog.threadDumpDelay)
        if (!collectSizeSampledFiles(project,
                                     retypeOptionsDialog.retypeExtension.removePrefix("."),
                                     retypeOptionsDialog.fileCount,
                                     queue)) return
        queue.processNext()
      }
    }
  }

  data class CandidateFile(val virtualFile: VirtualFile, val size: Long)

  private fun collectSizeSampledFiles(project: Project, extension: String, count: Int, queue: RetypeQueue): Boolean {
    val candidates = mutableListOf<CandidateFile>()
    val result = ProgressManager.getInstance().runProcessWithProgressSynchronously(
      {
        ProjectRootManager.getInstance(project).fileIndex.iterateContent { file ->
          ProgressManager.checkCanceled()
          if (file.extension == extension) {
            candidates.add(CandidateFile(file, file.length))
          }
          true
        }
      }, "Scanning files", true, project)
    if (!result) return false

    candidates.sortBy { it.size }
    if (count == 1) {
      queue.files.add(candidates[Random().nextInt(candidates.size)].virtualFile)
    }
    else {
      val stride = candidates.size / (count - 1)
      for (index in 0 until candidates.size step stride) {
        queue.files.add(candidates[index].virtualFile)
      }
    }
    return true
  }

  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)
    e.presentation.isEnabled = e.project != null
    val retypeSession = editor?.getUserData(RETYPE_SESSION_KEY)
    if (retypeSession != null) {
      e.presentation.text = "Stop Retyping"
    }
    else {
      e.presentation.text = "Retype File(s)"
    }
  }
}

interface RetypeFileAssistant {
  fun acceptLookupElement(element: LookupElement): Boolean

  companion object {
    val EP_NAME = ExtensionPointName.create<RetypeFileAssistant>("com.intellij.retypeFileAssistant")
  }
}

class RetypeQueue(private val project: Project, private val retypeDelay: Int, private val threadDumpDelay: Int) {
  val files = mutableListOf<VirtualFile>()

  fun processNext() {
    if (files.isEmpty()) return
    val file = files[0]
    files.removeAt(0)

    val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)
    val retypeSession = RetypeSession(project, editor as EditorImpl, retypeDelay, threadDumpDelay)
    if (files.isNotEmpty()) {
      retypeSession.startNextCallback = {
        ApplicationManager.getApplication().invokeLater { processNext() }
      }
    }
    retypeSession.start()
  }
}
