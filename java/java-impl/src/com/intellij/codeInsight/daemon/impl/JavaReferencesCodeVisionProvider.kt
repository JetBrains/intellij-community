// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.hints.codeVision.ReferencesCodeVisionProvider
import com.intellij.java.JavaBundle
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiTypeParameter

class JavaReferencesCodeVisionProvider : ReferencesCodeVisionProvider() {
  companion object{
    const val ID = "java.references"
  }

  override fun acceptsFile(file: PsiFile): Boolean = file.language == JavaLanguage.INSTANCE

  override fun acceptsElement(element: PsiElement): Boolean = element is PsiMember && element !is PsiTypeParameter

  override fun getHint(element: PsiElement, file: PsiFile): String? = JavaTelescope.usagesHint(element as PsiMember, file)

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