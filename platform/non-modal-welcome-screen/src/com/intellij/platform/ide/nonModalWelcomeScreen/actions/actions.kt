// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.icons.AllIcons
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog.TemplateName
import com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog.WelcomeScreenNewFileHandler
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.Icon

internal class CreateEmptyFileAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    WelcomeScreenNewFileHandler.showNewFileDialog(
      project = project,
      dialogTitle = NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.title.file"),
      templateName = TemplateName.Static("Generic Empty File")
    )
  }
}

@ApiStatus.Internal
open class NewEmptyFileAction(languageId: String, private val text: Supplier<@Nls String>, private val icon: Icon) :
  AnActionWrapper(createAction(getLanguage(languageId))) {

  private val language: Language? = getLanguage(languageId)

  override fun update(e: AnActionEvent) {
    super.update(e)
    if (e.presentation.isEnabledAndVisible && language == null) {
      e.presentation.isEnabledAndVisible = false
    }
    e.presentation.icon = icon
    e.presentation.setText(text)
  }

  override fun actionPerformed(e: AnActionEvent) {
    if (language == null) {
      return
    }
    val dataContext = SimpleDataContext.builder().setParent(e.dataContext).add(CommonDataKeys.LANGUAGE, language).build()
    super.actionPerformed(AnActionEvent.createEvent(dataContext, null, ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE, null))
  }
}

private fun createAction(language: Language?): AnAction {
  if (language == null) {
    return EmptyAction()
  }
  return ActionManager.getInstance().getAction("WelcomeNewEmptyFile") ?: EmptyAction()
}

private fun getLanguage(languageId: String): Language? {
  val language = Language.findLanguageByID(languageId)
  if (language != null) {
    return language
  }

  val fileType = FileTypeRegistry.getInstance().findFileTypeByName(languageId) ?: return null
  return LanguageUtil.getFileTypeLanguage(fileType)
}

internal class MarkdownNewFileAction :
  NewEmptyFileAction("Markdown", NonModalWelcomeScreenBundle.lazyMessage("action.WelcomeMarkdownNewFile.text"), AllIcons.FileTypes.Markdown)

internal class NewJavaFileAction :
  NewEmptyFileAction("JAVA", NonModalWelcomeScreenBundle.lazyMessage("action.NewJavaFile.text"), AllIcons.FileTypes.Java)

internal class NewKotlinFileAction :
  NewEmptyFileAction("Kotlin", NonModalWelcomeScreenBundle.lazyMessage("action.NewKotlinFile.text"), AllIcons.Language.Kotlin)