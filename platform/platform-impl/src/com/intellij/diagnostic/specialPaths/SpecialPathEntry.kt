package com.jetbrains.rider.diagnostics

import com.intellij.execution.ExecutionBundle
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VfsUtil
import com.jetbrains.rider.settings.RdClientDotnetBundle
import java.awt.datatransfer.StringSelection
import java.io.File

data class SpecialPathEntry(val name: String, private val originalPath: String, val kind: Kind) {
  enum class Kind {
    File,
    Folder
  }

  val path = FileUtilRt.toSystemDependentName(originalPath)

  /**
   * For Kind=File, it opens containing folder
   * For Kind=Folder, it opens the folder.
   */
  fun openDirectory() {
    when (kind) {
      Kind.Folder -> RevealFileAction.openDirectory(File(path))
      Kind.File -> RevealFileAction.openFile(File(path))
    }
  }

  fun openInEditor(project: Project) {
    if (kind == Kind.Folder)
      throw Exception("It's impossible to open folder in editor")
    val file = VfsUtil.findFileByIoFile(File(path), true)
    if (file == null)
      Notifications.Bus.notify(
        Notification(
          Notifications.SYSTEM_MESSAGES_GROUP_ID,
          ExecutionBundle.message("error.common.title"),
          RdClientDotnetBundle.message("notification.content.there.no.such.file", path),
          NotificationType.ERROR
        ), project
      )
    else
      FileEditorManager.getInstance(project).openFile(file, true, true)
  }

  fun open(project: Project?) {
    when (kind) {
      Kind.Folder -> openDirectory()
      Kind.File -> if (project != null) {
        openInEditor(project)
      }
      else {
        openDirectory()
      }
    }
  }

  fun getContextActionGroup(project: Project?, closeAction: () -> Unit) = DefaultActionGroup().apply {
    when (kind) {
      Kind.Folder -> {
        add(object : AnAction(RdClientDotnetBundle.messagePointer("action.open.folder.text"),
                              RdClientDotnetBundle.messagePointer("action.open.folder.description", path),
                              { null }) {
          override fun actionPerformed(e: AnActionEvent) {
            openDirectory()
            closeAction()
          }
        })
      }
      Kind.File -> {
        if (project != null) {
          add(object : AnAction(RdClientDotnetBundle.messagePointer("action.open.in.editor.text"),
                                RdClientDotnetBundle.messagePointer("action.open.in.editor.description", path),
                                { null }) {
            override fun actionPerformed(e: AnActionEvent) {
              openInEditor(project)
              closeAction()
            }
          })
        }
        add(object : AnAction(RdClientDotnetBundle.messagePointer("action.show.in.folder.text"),
                              RdClientDotnetBundle.messagePointer("action.show.in.folder.description", File(path).parent),
                              { null }) {
          override fun actionPerformed(e: AnActionEvent) {
            openDirectory()
            closeAction()
          }
        })
      }
    }
    add(object : AnAction(RdClientDotnetBundle.messagePointer("action.copy.path.text"),
                          RdClientDotnetBundle.messagePointer("action.copy.path.description", path),
                          { null }) {
      override fun actionPerformed(e: AnActionEvent) {
        CopyPasteManager.getInstance().setContents(StringSelection(path))
      }
    })
  }
}