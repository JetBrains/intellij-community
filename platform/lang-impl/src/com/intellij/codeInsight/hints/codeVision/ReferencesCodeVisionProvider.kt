// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.settings.language.isInlaySettingsEditor
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SyntaxTraverser
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

abstract class ReferencesCodeVisionProvider : DaemonBoundCodeVisionProvider {

  abstract fun acceptsFile(file: PsiFile): Boolean

  abstract fun acceptsElement(element: PsiElement): Boolean

  abstract fun getUsagesHint(element: PsiElement, file: PsiFile): String?

  abstract val usageClickedEventId: EventId?

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    if (!acceptsFile(file)) return emptyList()

    // we want to let this provider work only in tests dedicated for code vision, otherwise they harm performance
    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest(editor)) return emptyList()
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(file)
    for (element in traverser) {
      if (!acceptsElement(element)) continue
      if (!InlayHintsUtils.isFirstInLine(element)) continue
      val hint = getUsagesHint(element, file)
      if (hint == null) continue
      val handler = ClickHandler(element, usageClickedEventId)
      val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
      lenses.add(range to ClickableTextCodeVisionEntry(hint, id, handler))
    }
    return lenses
  }

  private class ClickHandler(
    element: PsiElement,
    private val usageClickedEventId: EventId?
  ) : (MouseEvent?, Editor) -> Unit {
    private val elementPointer = SmartPointerManager.createPointer(element)

    override fun invoke(event: MouseEvent?, editor: Editor) {
      if (isInlaySettingsEditor(editor)) return
      val element = elementPointer.element ?: return
      usageClickedEventId?.log(element.project)
      GotoDeclarationAction.startFindUsages(editor, element.project, element, if (event == null) null else RelativePoint(event))
    }
  }

  override fun getPlaceholderCollector(editor: Editor, psiFile: PsiFile?): CodeVisionPlaceholderCollector? {
    if (psiFile == null || !acceptsFile(psiFile)) return null
    return object: BypassBasedPlaceholderCollector {
      override fun collectPlaceholders(element: PsiElement, editor: Editor): List<TextRange> {
        if (!acceptsElement(element)) return emptyList()
        val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
        return listOf(range)
      }
    }
  }

  override val name: String
    get() = CodeInsightBundle.message("settings.inlay.hints.usages")
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val groupId: String
    get() = PlatformCodeVisionIds.USAGES.key
}