// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.SyntaxTraverser
import com.intellij.ui.awt.RelativePoint

class JavaReferencesCodeVisionProvider : JavaCodeVisionProviderBase() {
  companion object{
    const val ID = "java.references"
  }

  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(psiFile)
    for (element in traverser) {
      if (element !is PsiMember || element is PsiTypeParameter) continue
      val hint = JavaTelescope.usagesHint(element, psiFile)
      if (hint == null) continue
      lenses.add(Pair(element.textRange, ClickableTextCodeVisionEntry(hint, id, { it, _ ->
        JavaCodeVisionUsageCollector.USAGES_CLICKED_EVENT_ID.log(element.project)
        GotoDeclarationAction.startFindUsages(editor, element.project, element, if (it == null) null else RelativePoint(it))
      })))
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