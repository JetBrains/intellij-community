// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.ui.awt.RelativePoint
import java.awt.event.MouseEvent

class JavaReferencesCodeVisionProvider : JavaCodeVisionProviderBase() {
  companion object{
    const val ID = "java.references"
  }

  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    // we want to let this provider work only in tests dedicated for code vision, otherwise they harm performance
    if (ApplicationManager.getApplication().isUnitTestMode && !CodeVisionHost.isCodeLensTest(editor)) return emptyList()
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(psiFile)
    for (element in traverser) {
      if (element !is PsiMember || element is PsiTypeParameter) continue
      val hint = JavaTelescope.usagesHint(element, psiFile)
      if (hint == null) continue
      val handler = ClickHandler(element)
      val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
      lenses.add(range to ClickableTextCodeVisionEntry(hint, id, handler))
    }
    return lenses
  }

  private class ClickHandler(
    element: PsiElement,
  ) : (MouseEvent?, Editor) -> Unit {
    private val elementPointer = SmartPointerManager.createPointer(element)

    override fun invoke(event: MouseEvent?, editor: Editor) {
      val element = elementPointer.element ?: return
      JavaCodeVisionUsageCollector.USAGES_CLICKED_EVENT_ID.log(element.project)
      GotoDeclarationAction.startFindUsages(editor, element.project, element, if (event == null) null else RelativePoint(event))
    }
  }

  override fun collectPlaceholders(editor: Editor): List<TextRange> {
    val document = editor.document
    val project = editor.project ?: return emptyList()
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val psiFile = psiDocumentManager.getPsiFile(document)
    if (psiFile !is PsiJavaFile) return emptyList()
    val traverser = SyntaxTraverser.psiTraverser(psiFile)
    val lenses = ArrayList<TextRange>()
    for (element in traverser) {
      if (element !is PsiMember || element is PsiTypeParameter) continue
      val range = InlayHintsUtils.getTextRangeWithoutLeadingCommentsAndWhitespaces(element)
      lenses.add(range)
    }
    return lenses
  }


  override val name: String
    get() = JavaBundle.message("settings.inlay.java.usages")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore("java.inheritors"))
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = ID
  override val groupId: String
    get() = PlatformCodeVisionIds.USAGES.key
}