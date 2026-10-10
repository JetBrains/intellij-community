// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

fun <E : PsiElement> E.createSmartPointer(): SmartPsiElementPointer<E> =
  SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)

/**
 * @param rangeInElement the place inside the element; null for the whole element
 * @return the place of [rangeInElement], which holds no PSI; null for an element without a file, such as a directory, and for a file
 * without a text range, such as a binary file
 */
@ApiStatus.Experimental
@RequiresReadLock
@JvmOverloads
fun PsiElement.createSmartFileRange(rangeInElement: TextRange? = null): SmartPsiFileRange? {
  val file = containingFile ?: return null
  val elementRange = (if (this is PsiFile) file.textRange else textRange) ?: return null
  val range = rangeInElement?.shiftRight(elementRange.startOffset) ?: elementRange
  return SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)
}

/**
 * Finds the PSI of the place again, as it is now. The element may be a parent with the same range, so use it for display only.
 *
 * @return the file if the range is the whole file, or no element has exactly the range; otherwise the largest element with the range;
 * null if the file or the range is gone
 */
@ApiStatus.Experimental
@RequiresReadLock
fun SmartPsiFileRange.findElement(): PsiElement? {
  val file = element ?: return null
  val fileRange = range ?: return null
  if (fileRange.startOffset == 0 && fileRange.endOffset == file.textLength) return file
  return PsiTreeUtil.findElementOfClassAtRange(file, fileRange.startOffset, fileRange.endOffset, PsiElement::class.java) ?: file
}
