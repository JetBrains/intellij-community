// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.internal

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.uast.UastLanguagePlugin
import org.jetbrains.uast.asRecursiveLogString
import org.jetbrains.uast.toUElement

open class DumpUastTreeAction : AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(LangDataKeys.PSI_FILE) ?: return
    val project = e.project ?: return

    val dump = runReadAction { buildDump(file) }
    if (dump == null) {
      Notifications.Bus.notify(
        Notification("UAST", JvmAnalysisBundle.message("title.uast"),
                     "${JvmAnalysisBundle.message("can.t.build.uast.tree.for.file")} '${file.name}'", NotificationType.ERROR)
      )
      return
    }

    FileEditorManager.getInstance(project).openEditor(
      OpenFileDescriptor(project, LightVirtualFile(file.name + ".uast-log", PlainTextLanguage.INSTANCE, dump)),
      true
    )
  }

  open fun buildDump(file: PsiFile): String? = file.toUElement()?.asRecursiveLogString()

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = ApplicationManager.getApplication().isInternal && run {
      val file = e.getData(LangDataKeys.PSI_FILE) ?: return@run false
      UastLanguagePlugin.byLanguage(file.language) != null
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}