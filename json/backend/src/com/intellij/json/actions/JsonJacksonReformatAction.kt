// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.actions

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.json.JsonBundle
import com.intellij.json.JsonFileType
import com.intellij.json.JsonUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class JsonJacksonReformatAction : AnAction(), LargeFileWriteRequestor {
  override fun update(e: AnActionEvent) {
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = ApplicationManager.getApplication().isInternal
                                         && virtualFile != null
                                         && JsonUtil.isJsonFile(virtualFile, e.project)
  }


  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    val project = e.project!!
    runWithModalProgressBlocking(project, JsonBundle.message("JsonJacksonReformatAction.progress.title.json.reformatting")) {

      val formatted = withContext(Dispatchers.IO) {
        val objectMapper = ObjectMapper()
        val parsed = virtualFile.inputStream.use { objectMapper.readTree(it) }
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed)
      }

      val doc = readAction {
        PsiManager.getInstance(project).findFile(virtualFile)?.let { PsiDocumentManager.getInstance(project).getDocument(it) }
      }

      if (doc?.isWritable == true) {
        edtWriteAction {
          executeCommand(e.project, JsonBundle.message("JsonJacksonReformatAction.command.name.json.reformat")) {
            doc.setText(formatted)
          }
        }
        return@runWithModalProgressBlocking
      }

      if (!virtualFile.isWritable) {
        val file = LightVirtualFile(virtualFile.nameWithoutExtension + "-formatted.json", JsonFileType.INSTANCE, formatted)
        val descriptor = OpenFileDescriptor(project, file)
        withContext(Dispatchers.EDT) {
          FileEditorManager.getInstance(project).openEditor(descriptor, true)
        }
        return@runWithModalProgressBlocking
      }

      val dialogBuilder = MessageDialogBuilder.okCancel(
        JsonBundle.message("JsonJacksonReformatAction.dialog.title.json.reformatting"),
        JsonBundle.message("JsonJacksonReformatAction.dialog.message.this.action.not.undoable.do.you.want.to.reformat.document")
      )
      if (!dialogBuilder.ask(project)) return@runWithModalProgressBlocking

      edtWriteAction {
        executeCommand(e.project, JsonBundle.message("JsonJacksonReformatAction.command.name.json.reformat")) {
          virtualFile.getOutputStream(this@JsonJacksonReformatAction).use { stream ->
            stream.write(formatted.toByteArray(virtualFile.getCharset()))
          }
        }
      }
    }

  }

}