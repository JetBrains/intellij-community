// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.LanguageSurrounders
import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SurroundWithActionBase : AnAction() {

  private fun isMySurrounder(surrounder: Surrounder): Boolean {
    return templatePresentation.text == surrounder.templateDescription
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    val file = e.getData(CommonDataKeys.PSI_FILE)
    if (project == null || editor == null || file == null) return
    if (!FileDocumentManager.getInstance().requestWriting(editor.document, project)) return
    val surroundContext = findSurroundContext(file, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd) ?: return
    SurroundWithHandler.doSurround(project, editor, surroundContext.surrounder, surroundContext.elements)
  }

  override fun update(e: AnActionEvent) {
    if (!e.place.contains(ActionPlaces.EDITOR_FLOATING_TOOLBAR)) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val project = e.project
    val editor = e.getData(CommonDataKeys.EDITOR)
    val file = e.getData(CommonDataKeys.PSI_FILE)
    if (project == null || editor == null || file == null) return
    val surroundContext = findSurroundContext(file, editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
    e.presentation.isEnabled = (surroundContext != null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private fun findSurroundContext(file: PsiFile, startOffset: Int, endOffset: Int): SurroundContext? {
    val language = file.viewProvider.baseLanguage
    val descriptors: MutableList<SurroundDescriptor> = LanguageSurrounders.INSTANCE.allForLanguage(language)
    return descriptors.firstNotNullOfOrNull { descriptor -> findSurroundContext(descriptor, file, startOffset, endOffset) }
  }

  private fun findSurroundContext(descriptor: SurroundDescriptor, file: PsiFile, startOffset: Int, endOffset: Int): SurroundContext? {
    val surrounder = descriptor.surrounders.firstOrNull(::isMySurrounder) ?: return null
    val elements = descriptor.getElementsToSurround(file, startOffset, endOffset)
    if (elements.isEmpty()) return null
    return SurroundContext(surrounder, elements)
  }

  class SurroundContext(val surrounder: Surrounder, val elements: Array<PsiElement>)
}