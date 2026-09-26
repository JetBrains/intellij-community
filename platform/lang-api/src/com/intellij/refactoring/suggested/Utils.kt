// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.suggested

import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.asTextRange
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("Use TextRange(offset, offset + DocumentEvent.oldLength) directly")
val DocumentEvent.oldRange: TextRange
  get() = TextRange(offset, offset + oldLength)

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("Use range from com.intellij.openapi.editor",
            replaceWith = ReplaceWith("this.asTextRange", "com.intellij.openapi.editor.range.asTextRange"))
val RangeMarker.range: TextRange?
  get() = this.asTextRange

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("Use PsiElement.startOffset from com.intellij.psi",
            replaceWith = ReplaceWith("this.startOffset", "com.intellij.psi.util.startOffset"))
val PsiElement.startOffset: Int
  get() = textRange.startOffset

@get:ApiStatus.ScheduledForRemoval
@get:Deprecated("Use PsiElement.endOffset from com.intellij.psi",
            replaceWith = ReplaceWith("this.endOffset", "com.intellij.psi.util.endOffset"))
val PsiElement.endOffset: Int
  get() = textRange.endOffset

@ApiStatus.ScheduledForRemoval
@Deprecated("Use alternative method from com.intellij.psi package",
            ReplaceWith("this.createSmartPointer()", "com.intellij.psi.createSmartPointer"))
fun <E : PsiElement> E.createSmartPointer(): SmartPsiElementPointer<E> =
  SmartPointerManager.getInstance(project).createSmartPsiElementPointer(this)
