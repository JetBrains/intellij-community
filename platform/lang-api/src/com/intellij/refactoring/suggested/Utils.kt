// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

val DocumentEvent.oldRange: TextRange
  get() = TextRange(offset, offset + oldLength)

val DocumentEvent.newRange: TextRange
  get() = TextRange(offset, offset + newLength)

val RangeMarker.range: TextRange?
  get() {
    if (!isValid) return null
    val start = startOffset
    val end = endOffset
    return if (start in 0..end) {
      TextRange(start, end)
    }
    else {
      // Probably a race condition had happened and range marker is invalidated
      null
    }
  }

val PsiElement.startOffset: Int
  get() = textRange.startOffset

val PsiElement.endOffset: Int
  get() = textRange.endOffset

fun <E : PsiElement> E.createSmartPointer(): SmartPsiElementPointer<E> =
  SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)

fun PsiFile.hasErrorElementInRange(range: TextRange): Boolean {
  var leaf = findElementAt(range.startOffset) ?: return false
  var leafRange = leaf.textRange
  if (leafRange.startOffset < range.startOffset) {
    leaf = PsiTreeUtil.nextLeaf(leaf, true) ?: return false
    leafRange = leaf.textRange
  }
  assert(leafRange.startOffset >= range.startOffset)

  var endOffset = leafRange.endOffset
  while (endOffset <= range.endOffset) {
    if (leaf is PsiErrorElement || leaf.parent is PsiErrorElement) return true
    leaf = PsiTreeUtil.nextLeaf(leaf, false) ?: return false
    endOffset += leaf.textLength
  }
  return false
}

inline fun <reified T : PsiElement> PsiElement.findDescendantOfType(noinline predicate: (T) -> Boolean = { true }): T? {
  return findDescendantOfType({ true }, predicate)
}

inline fun <reified T : PsiElement> PsiElement.findDescendantOfType(
  crossinline canGoInside: (PsiElement) -> Boolean,
  noinline predicate: (T) -> Boolean = { true }
): T? {
  var result: T? = null
  this.accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement) {
      if (element is T && predicate(element)) {
        result = element
        stopWalking()
        return
      }

      if (canGoInside(element)) {
        super.visitElement(element)
      }
    }
  })
  return result
}
