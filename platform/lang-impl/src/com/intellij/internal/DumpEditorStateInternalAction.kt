// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.RevealFileAction
import com.intellij.lang.LangBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.util.PathUtil
import java.io.IOException
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.writeText

private val FILE_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

internal class DumpEditorStateInternalAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl ?: return

    val sourceName = e.getData(CommonDataKeys.VIRTUAL_FILE)?.name?.takeIf { it.isNotEmpty() } ?: "editor"
    val sanitizedSourceName = PathUtil.suggestFileName(sourceName).ifEmpty { "editor" }
    val timestamp = FILE_TIMESTAMP_FORMATTER.format(ZonedDateTime.now())
    val file = FileChooser.chooseFile(FileChooserDescriptorFactory.singleDir(),
                                      project,
                                      null)
      ?.toNioPathOrNull()
      ?.resolve("editorStateDump-$sanitizedSourceName-$timestamp.txt") ?: return

    val group = NotificationGroupManager.getInstance().getNotificationGroup(EDITOR_STATE_DUMP_NOTIFICATION_GROUP)
    val notification = try {
      file.writeText(editor.dumpState())
      group.createNotification(IdeBundle.message("editor.state.dump.success"), NotificationType.INFORMATION)
        .addAction(NotificationAction.createSimpleExpiring(LangBundle.message("button.open.in.editor")) {
          openFileInEditor(project, file)
        })
        .addAction(object : RevealFileAction() {
          override fun actionPerformed(e: AnActionEvent) {
            openFile(file)
          }
        })
    }
    catch (exception: IOException) {
      LOG.info("Failed to write the editor state dump file", exception)
      group.createNotification(IdeBundle.message("editor.state.dump.failed"), NotificationType.ERROR)
    }
    notification.notify(project)
  }

  private fun openFileInEditor(project: com.intellij.openapi.project.Project, file: Path) {
    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file) ?: return
    FileEditorManager.getInstance(project).openEditor(OpenFileDescriptor(project, virtualFile), true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR) as? EditorImpl
    e.presentation.isEnabled = project != null && editor != null
  }

  private companion object {
    private const val EDITOR_STATE_DUMP_NOTIFICATION_GROUP = "editor.state.dump"
    private val LOG = logger<DumpEditorStateInternalAction>()
  }
}
