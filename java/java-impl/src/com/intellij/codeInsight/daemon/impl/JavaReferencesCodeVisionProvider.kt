// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.codeInsight.hints.codeVision.RenameAwareReferencesCodeVisionProvider
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiTypeParameter

class JavaReferencesCodeVisionProvider : RenameAwareReferencesCodeVisionProvider() {
  companion object {
    const val ID: String = "java.references"
  }

  override fun acceptsFile(file: PsiFile): Boolean = file.language == JavaLanguage.INSTANCE

  override fun acceptsElement(element: PsiElement): Boolean = element is PsiMember && element !is PsiTypeParameter

  private fun getVisionInfo(element: PsiElement, file: PsiFile): CodeVisionProviderBase.CodeVisionInfo? {
    val inspection = UnusedDeclarationInspectionBase.findUnusedDeclarationInspection(element)
    if (inspection.isEntryPoint(element)) return null
    return JavaTelescope.usagesHint(element as PsiMember, file)?.let {
      CodeVisionProviderBase.CodeVisionInfo(it.hint, it.count)
    }
  }

  override fun getHint(element: PsiElement, file: PsiFile): String? {
    return getVisionInfo(element, file)?.text
  }

  override fun logClickToFUS(element: PsiElement, hint: String) {
    JavaCodeVisionUsageCollector.USAGES_CLICKED_EVENT_ID.log(element.project)
  }

  override val name: String
    get() = JavaBundle.message("settings.inlay.java.usages")
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingBefore("java.inheritors"))
  override val id: String
    get() = ID
}