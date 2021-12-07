// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.SyntaxTraverser

class JavaReferencesCodeVisionProvider : JavaCodeVisionProviderBase() {

  override fun computeLenses(editor: Editor, psiFile: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
    val lenses = ArrayList<Pair<TextRange, CodeVisionEntry>>()
    val traverser = SyntaxTraverser.psiTraverser(psiFile)
    for (element in traverser) {
      if (element !is PsiMember || element is PsiTypeParameter) continue
      val hint = JavaTelescope.usagesHint(element, psiFile)
      if (hint == null) continue
      lenses.add(Pair(element.textRange, TextCodeVisionEntry(hint, id, null, hint, "", emptyList())))
    }
    return lenses
  }

  override fun handleClick(editor: Editor, textRange: TextRange) {

  }

  override val name: String
    get() = JavaBundle.message("settings.inlay.java.usages")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Default
  override val id: String
    get() = "java.references"
  override val groupId: String
    get() = PlatformCodeVisionIds.USAGES.key
}