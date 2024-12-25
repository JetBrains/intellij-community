// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.Language
import com.intellij.microservices.MicroservicesBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.semantic.SemService
import javax.swing.event.HyperlinkEvent

@Suppress("IntentionDescriptionNotFoundInspection")
internal class UrlPathInlayHintsDisableIntention : UrlPathInlaySettingsIntention(false) {
  override fun getFamilyName(): String = MicroservicesBundle.message("microservices.inlay.hide.inlays.intention.name")
}

@Suppress("IntentionDescriptionNotFoundInspection")
internal class UrlPathInlayHintsEnableIntention : UrlPathInlaySettingsIntention(true) {
  override fun getFamilyName(): String = MicroservicesBundle.message("microservices.inlay.show.inlays.enable.intention.name")
}

internal sealed class UrlPathInlaySettingsIntention(private val shouldBeEnabled: Boolean) : PsiElementBaseIntentionAction() {
  override fun getText(): String = familyName

  override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
    if (editor == null) return false
    if (UrlPathInlayHintsProvider.isUrlPathInlaysEnabledForLanguage(element.language) == shouldBeEnabled) return false
    return shouldHaveUrlPathInlayAroundOffset(element, editor.caretModel.offset)
  }

  override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
    UrlPathInlayHintsProvider.setUrlPathInlaysEnabledForLanguage(element.language, shouldBeEnabled)
    InlayHintsPassFactoryInternal.restartDaemonUpdatingHints(project, "UrlPathInlaySettingsIntention.invoke")

    ApplicationManager.getApplication().invokeLater(
      {
        showHintToSettings(project, editor!!, element.language)
      }, ModalityState.nonModal(), project.disposed)
  }

  private fun showHintToSettings(project: Project, editor: Editor, language: Language) {
    val htmlBuilder = HtmlBuilder()
      .append(MicroservicesBundle.message("microservices.inlay.settings.inlays.intention.hint.message", if (shouldBeEnabled) 0 else 1))
      .appendLink("link", MicroservicesBundle.message("microservices.inlay.settings.inlays.intention.hint.link"))
    HintManager.getInstance().showInformationHint(editor, htmlBuilder.toString()) {
      if (it.eventType != HyperlinkEvent.EventType.ACTIVATED) return@showInformationHint
      UrlPathInlayHintsProvider.Companion.openUrlPathInlaySettings(project, language)
    }
  }

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? {
    return null
  }

  override fun getFileModifierForPreview(target: PsiFile): FileModifier? {
    return null
  }

  companion object {
    internal fun shouldHaveUrlPathInlayAroundOffset(
      element: PsiElement,
      offset: Int,
      searchLimit: Int = Int.MAX_VALUE
    ): Boolean {
      val languagesProvider = getLanguagesProviderByLanguage(element.language) ?: return false
      val semService = SemService.getSemService(element.project)
      return generateSequence(element) { it.parent?.takeIf { parent -> parent !is PsiFile } }
        .take(searchLimit)
        .flatMap { UrlPathInlayHintsProvider.Companion.inlaysInElement(languagesProvider, it, semService) }
        .flatMap { it.inlayHints }
        .mapNotNull { it.attachedTo?.element }
        .any { offset in it.textRange.grown(1) }
    }
  }
}