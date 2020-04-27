// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer

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
