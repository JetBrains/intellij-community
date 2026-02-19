// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.writeText
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@Suppress("HardCodedStringLiteral", "ActionPresentationInstantiatedInCtor")
@ApiStatus.Internal
class ShowSettingsChangesLogFile : DumbAwareAction("Show Settings Changes Log File") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val file = FileUtilRt.createTempFile("settings-changes", ".txt", true)
    val tempFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
    val editor = FileEditorManager.getInstance(project).openFile(tempFile, true)

    val isLoggingEnabled = System.getProperty(LoggingSettingsChangesListener.JVM_PROPERTY_KEY, "false").toBoolean()
    val initialText = if (isLoggingEnabled)
      "// 1. Call 'Save All' action first\n" +
      "// 2. Remove the file content\n" +
      "// 3. Change the setting you want to observe\n" +
      "// 4. Call 'Save All' action again\n\n"
    else
      "The persistent settings saving logging seems to be disabled.\n" +
      "To enable it, please add a JVM property '-Dide.settings.log.persistent.changes=true' and restart the IDE.\n"

    ApplicationManager.getApplication().invokeLater {
      WriteAction.run<IOException> {
        tempFile.writeText(initialText)
      }
    }

    if (isLoggingEnabled) {
      ApplicationManager.getApplication().messageBus.connect(editor[0]).subscribe(LoggingSettingsChangesListener.TOPIC, object : LoggingSettingsChangesListener {
        override fun performed(event: LoggingSettingsChangesListener.Event) {
          LOG.debug(event.change)

          ApplicationManager.getApplication().invokeLater {
            WriteAction.run<IOException> {
              val currentText = VfsUtil.loadText(tempFile)
              tempFile.writeText(currentText + event.change + "\n")
            }
          }
        }
      })
    }

    Disposer.register(editor[0]) {
      ApplicationManager.getApplication().invokeLater {
        WriteAction.run<IOException> {
          tempFile.delete(this)
        }
      }
    }
  }

  companion object {
    private val LOG get() = Logger.getInstance(ShowSettingsChangesLogFile::class.java)
  }
}
