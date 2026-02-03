// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings.getTemplateableLanguages
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.containers.ContainerUtil

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

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    return psiFile?.viewProvider is ConfigurableTemplateLanguageFileViewProvider
  }

  override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {
    if (psiFile == null || editor == null || psiFile.viewProvider !is ConfigurableTemplateLanguageFileViewProvider) return

    val sortedLanguages = ContainerUtil.sorted(getTemplateableLanguages(), Comparator.comparing { obj: Language -> obj.displayName })
    ListPopupImpl(project, TemplateDataLanguageChooserPopupStep(sortedLanguages, psiFile.virtualFile, project))
      .showInBestPositionFor(editor)
  }
}