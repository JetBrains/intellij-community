/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import java.util.*

public object CompletionMemory {
  private val LAST_CHOSEN_METHODS = Key.create<LinkedList<RangeMarker>>("COMPLETED_METHODS")
  private val CHOSEN_METHODS = Key.create<SmartPsiElementPointer<PsiMethod>>("CHOSEN_METHODS")

  @JvmStatic
  public fun registerChosenMethod(method: PsiMethod, call: PsiCall) {
    val nameRange = getAnchorRange(call) ?: return
    val document = call.containingFile.viewProvider.document ?: return
    addToMemory(document, createChosenMethodMarker(document, CompletionUtil.getOriginalOrSelf(method), nameRange))
  }

  private fun getAnchorRange(call: PsiCall) = when (call) {
    is PsiMethodCallExpression -> call.methodExpression.referenceNameElement?.textRange
    is PsiNewExpression -> call.classOrAnonymousClassReference?.referenceNameElement?.textRange
    else -> null
  }

  private fun addToMemory(document: Document, marker: RangeMarker) {
    val completedMethods = LinkedList<RangeMarker>()
    document.getUserData(LAST_CHOSEN_METHODS)?.let { completedMethods.addAll(it.filter { it.isValid && !haveSameRange(it, marker) }) }
    while (completedMethods.size > 10) {
      completedMethods.removeAt(0)
    }
    document.putUserData(LAST_CHOSEN_METHODS, completedMethods)
    completedMethods.add(marker)
  }

  private fun createChosenMethodMarker(document: Document, method: PsiMethod, nameRange: TextRange): RangeMarker {
    val marker = document.createRangeMarker(nameRange.startOffset, nameRange.endOffset)
    marker.putUserData(CHOSEN_METHODS, SmartPointerManager.getInstance(method.project).createSmartPsiElementPointer(method))
    return marker
  }

  @JvmStatic
  public fun getChosenMethod(call: PsiCall): PsiMethod? {
    val range = getAnchorRange(call) ?: return null
    val completedMethods = call.containingFile.originalFile.viewProvider.document?.getUserData(LAST_CHOSEN_METHODS)
    val marker = completedMethods?.let { it.find { m -> haveSameRange(m, range) } }
    return marker?.getUserData(CHOSEN_METHODS)?.element
  }

  private fun haveSameRange(s1: Segment, s2: Segment) = s1.startOffset == s2.startOffset && s1.endOffset == s2.endOffset

}