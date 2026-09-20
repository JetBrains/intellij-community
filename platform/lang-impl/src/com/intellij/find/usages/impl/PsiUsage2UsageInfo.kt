// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.DynamicUsage
import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.usageView.UsageInfo
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PsiUsage2UsageInfo(psiUsage: PsiUsage) : UsageInfo(psiUsage.file, psiUsage.range, psiUsage is PlainTextUsage) {
  init {
    isDynamicUsage = psiUsage is DynamicUsage && psiUsage.isDynamic
  }

  private val pointer: Pointer<out PsiUsage> = psiUsage.createPointer()

  override fun isValid(): Boolean = super.isValid() && pointer.dereference() != null

  val psiUsage: PsiUsage get() = requireNotNull(pointer.dereference())

  /**
   * The smallest element that spans the usage range, or the file when the range spans no element.
   * The usage is anchored on the file range, so a reference-like element is derived on each call and never goes stale.
   * A usage view groups such a usage by its method or class, and a run context action reads the element position.
   */
  override fun getElement(): PsiElement? {
    val element = super.getElement() ?: return null
    if (element !is PsiFile) return element
    val range = pointer.dereference()?.range ?: return element
    if (range.isEmpty) return element
    val start = element.findElementAt(range.startOffset) ?: return element
    val end = element.findElementAt(range.endOffset - 1) ?: return element
    return PsiTreeUtil.findCommonParent(start, end) ?: element
  }
}
