// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.json.JsonBundle
import com.intellij.jsonpath.ui.JsonPathEvaluateManager.Companion.JSON_PATH_EVALUATE_RESULT_KEY
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VfsUtil

internal class JsonPathExportEvaluateResultAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return

    if (editor.getUserData(JSON_PATH_EVALUATE_RESULT_KEY) != true) return

    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      Runnable {
        WriteCommandAction.runWriteCommandAction(project, JsonBundle.message("jsonpath.evaluate.export.result"), null, Runnable {
          val file = ScratchRootType.getInstance()
            .findFile(project, "jsonpath-result.json", ScratchFileService.Option.create_new_always)

          VfsUtil.saveText(file, editor.document.text)

          val fileEditorManager = FileEditorManager.getInstance(project)
          if (!fileEditorManager.isFileOpen(file)) {
            fileEditorManager.openEditor(OpenFileDescriptor(project, file), true)
          }
        })
      }, JsonBundle.message("jsonpath.evaluate.progress.export.result"), false, project)
  }

  override fun update(e: AnActionEvent) {
    val editor = e.getData(CommonDataKeys.EDITOR)

    e.presentation.isEnabledAndVisible = editor != null && editor.getUserData(JSON_PATH_EVALUATE_RESULT_KEY) == true
  }
}