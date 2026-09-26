// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.application.options.schemes.SchemeNameGenerator
import com.intellij.ide.actions.QuickChangeColorSchemeAction
import com.intellij.lang.LangBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.FileDropEvent
import com.intellij.openapi.editor.FileDropHandler
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.editor.colors.impl.EmptyColorScheme
import com.intellij.openapi.options.SchemeImportException
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class EditorColorSchemeDropHandler : FileDropHandler {
  override suspend fun handleDrop(e: FileDropEvent): Boolean {
    val ioFile = if (e.files.size == 1) e.files.firstOrNull() else null
    if (ioFile == null || !StringUtil.endsWithIgnoreCase(ioFile.name, ".icls")) return false

    val file = VfsUtil.findFileByIoFile(ioFile, true) ?: return false

    withContext(Dispatchers.EDT) {
      if (requestConfirmation(file, e)) {
        try {
          importScheme(file, e)
        }
        catch (ex: SchemeImportException) {
          val title = if (ex.isWarning) LangBundle.message("notification.title.color.scheme.added")
          else LangBundle.message("notification.title.color.scheme.import.failed")

          val type = if (ex.isWarning) NotificationType.WARNING else NotificationType.ERROR
          val notification = Notification("ColorSchemeDrop", title, ex.message!!, type)
          notification.notify(e.project)
        }
      }
    }

    return true
  }
}

private fun requestConfirmation(file: VirtualFile, e: FileDropEvent): Boolean {
  return MessageDialogBuilder.yesNo(
    LangBundle.message("dialog.title.install.color.scheme"),
    LangBundle.message("message.would.you.like.to.install.and.apply.0.editor.color.scheme", file.name)
  )
    .yesText(LangBundle.message("button.install"))
    .noText(LangBundle.message("button.open.in.editor"))
    .ask(e.project)
}

private fun importScheme(file: VirtualFile, e: FileDropEvent) {
  val importer = ColorSchemeImporter()
  val colorsManager = EditorColorsManager.getInstance()
  val oldScheme = colorsManager.globalScheme
  val names = colorsManager.allSchemes.map { obj: EditorColorsScheme -> obj.name }

  val imported = importer.importScheme(DefaultProjectFactory.getInstance().defaultProject, file, colorsManager.globalScheme) { name: String? ->
    val preferredName = name ?: "Unnamed"
    val newName = SchemeNameGenerator.getUniqueName(preferredName) { candidate: String -> names.contains(candidate) }
    val newScheme = EditorColorsSchemeImpl(EmptyColorScheme.getEmptyScheme())
    newScheme.name = newName
    newScheme.setDefaultMetaInfo(EmptyColorScheme.getEmptyScheme())
    newScheme
  }

  if (imported != null) {
    colorsManager.addColorScheme(imported)
    val message = importer.getAdditionalImportInfo(imported)
                  ?: ApplicationBundle.message("settings.editor.scheme.import.success", file.presentableUrl, imported.name)

    colorsManager.setGlobalScheme(imported)
    val notification = Notification("ColorSchemeDrop", LangBundle.message("notification.title.color.scheme.added"), message, NotificationType.INFORMATION)
    QuickChangeColorSchemeAction.changeLafIfNecessary(oldScheme, imported) {
      Alarm().addRequest({ Notifications.Bus.notify(notification, e.project) }, 300)
    }
  }
}