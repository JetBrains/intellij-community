// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.application.options.codeStyle.cache.TooFrequentCodeStyleComputationWatcher
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import java.io.IOException
import kotlin.io.path.writeText

internal class DumpCodeStyleCacheAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = FileChooser.chooseFile(FileChooserDescriptorFactory.singleDir(),
                                      project,
                                      null)
      ?.toNioPathOrNull()
      ?.resolve("codeStyleCacheDump.txt") ?: return

    val dump = buildString {
      @Suppress("DEPRECATION")
      appendLine("Project settings: ${CodeStyleSettingsManager.getInstance(project).currentSettings}")
      TooFrequentCodeStyleComputationWatcher.getInstance(project).dumpState(this, -1.0)
    }
    val group = NotificationGroupManager.getInstance().getNotificationGroup("code.style.cache.dump")
    val notification = try {
      file.writeText(dump)
      group.createNotification(IdeBundle.message("code.style.cache.dump.success"), NotificationType.INFORMATION)
        .addAction(object : RevealFileAction() {
          override fun actionPerformed(e: AnActionEvent) {
            openFile(file)
          }
        })
    }
    catch (e: IOException) {
      thisLogger().info("Failed to write the cache dump file", e)
      group.createNotification(IdeBundle.message("code.style.cache.dump.failed"), NotificationType.ERROR)
    }
    notification.notify(project)
  }
}