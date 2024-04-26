// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings.getTemplateableLanguages
import com.intellij.ui.popup.list.ListPopupImpl

internal class ChooseTemplateDataLanguageIntention : IntentionAction {
  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getFamilyName(): String {
    return LangBundle.message("template.data.language.chooser.intention.family.name")
  }

  override fun getText(): String {
    return LangBundle.message("template.data.language.chooser.intention.title")
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return file?.viewProvider is ConfigurableTemplateLanguageFileViewProvider
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    if (file == null || editor == null || file.viewProvider !is ConfigurableTemplateLanguageFileViewProvider) return

    ListPopupImpl(project, TemplateDataLanguageChooserPopupStep(getTemplateableLanguages(), file.virtualFile, project))
      .showInBestPositionFor(editor)
  }
}