// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.settings.language.isInlaySettingsEditor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent

abstract class CodeVisionProviderBase : DaemonBoundCodeVisionProvider {

  /**
   * WARNING! This method is executed also before the file is open. It must be fast! During it users see no editor.
   * @return true iff this provider may provide lenses for this file.
   */
  abstract fun acceptsFile(file: PsiFile): Boolean

  /**
   * WARNING! This method is executed also before the file is open. It must be fast! During it users see no editor.
   * @return true iff this provider may provide lenses for this element.
   */
  abstract fun acceptsElement(element: PsiElement): Boolean

  /**
   * @return text that user sees for a given element as a code lens
   */
  @Nls
  abstract fun getHint(element: PsiElement, file: PsiFile): String?

  /**
   * The default text-only implementation. Override to provide the correct count.
   * @return text, count of, and if the count is exact for a given element as a code lens
   */
  open fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionInfo? {
    val hint = getHint(element, file) ?: return null
    return CodeVisionInfo(hint)
  }

  /**
   * @param hint result of [getHint]
   */
  open fun logClickToFUS(element: PsiElement, hint: String) {}

  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>>? {
    if (file.project.isDefault) return null
    if (!acceptsFile(file)) return null

    // we want to let this provider work only in tests dedicated for code vision, otherwise they harm performance
    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest()) return emptyList()

    val virtualFile = file.viewProvider.virtualFile
    if (ProjectFileIndex.getInstance(file.project).isInLibrarySource(virtualFile)) return emptyList()

    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(file)
    for (element in traverser) {
      if (!acceptsElement(element)) continue
      if (!InlayHintsUtils.isFirstInLine(element)) continue
      val hint = getHint(element, file)
      if (hint == null) continue
      val handler = ClickHandler(element, hint)
      val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
      lenses.add(range to ClickableTextCodeVisionEntry(hint, id, handler))
    }
    return lenses
  }

  private inner class ClickHandler(
    element: PsiElement,
    private val hint: String,
  ) : (MouseEvent?, Editor) -> Unit {
    private val elementPointer = SmartPointerManager.createPointer(element)

    override fun invoke(event: MouseEvent?, editor: Editor) {
      if (isInlaySettingsEditor(editor)) return
      val element = elementPointer.element ?: return
      logClickToFUS(element, hint)
      handleClick(editor, element, event)
    }
  }

  abstract fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?)

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

  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default

  /**
   * Code vision item information
   * @param text Label of the item that is displayed in the interline
   * @param count If the item represents a counter, the count, null otherwise
   * @param countIsExact Whether the counter represents the exact count or a lower bound estimate
   *    (the latter can happen if computing the exact count is slow)
   */
  class CodeVisionInfo(
    @Nls @get:Nls
    val text: String,
    val count: Int? = null,
    val countIsExact: Boolean = true
  )
}