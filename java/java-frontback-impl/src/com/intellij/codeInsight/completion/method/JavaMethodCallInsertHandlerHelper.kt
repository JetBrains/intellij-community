// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.method

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.OffsetKey
import com.intellij.codeInsight.completion.method.JavaMethodCallInsertHandlerHelper.findInsertedCall
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiCallExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil


object JavaMethodCallInsertHandlerHelper {
  @JvmStatic
  fun getReferenceStartOffset(context: InsertionContext, item: LookupElement): Int {
    val refStartKey = requireNotNull(item.getUserData(refStartKey)) { "refStartKey must have been set" }
    return context.offsetMap.getOffset(refStartKey)
  }

  @JvmStatic
  fun findCallAtOffset(context: InsertionContext, offset: Int): PsiCallExpression? {
    context.commitDocument()
    return PsiTreeUtil.findElementOfClassAtOffset(context.file, offset, PsiCallExpression::class.java, false)
  }

  /**
   * Use [findInsertedCall] to get PsiCallExpression representing the inserted code, or null if no code was inserted
   * Can be called in [JavaMethodCallInsertHandler]'s afterHandler
   */
  @JvmStatic
  fun findInsertedCall(element: LookupElement, context: InsertionContext): PsiCallExpression? {
    return element.getUserData(callKey)
  }

  internal fun installRefStartKey(context: InsertionContext, item: LookupElement) {
    val refStart = OffsetKey.create("refStart", true)
    context.offsetMap.addOffset(refStart, context.startOffset)
    item.putUserData(refStartKey, refStart)
  }

  internal fun installCall(context: InsertionContext, item: LookupElement) {
    val methodCall = findCallAtOffset(context, getReferenceStartOffset(context, item)) ?: return

    // make sure this is the method call we've just added, not the enclosing one
    val completedElement = (methodCall as? PsiMethodCallExpression)?.methodExpression?.referenceNameElement
    val completedElementRange = completedElement?.textRange ?: return
    if (completedElementRange.startOffset != getReferenceStartOffset(context, item)) return

    item.putUserData(callKey, methodCall)
  }
}

private val callKey = Key.create<PsiCallExpression>("JavaMethodCallInsertHandler.call")
private val refStartKey = Key.create<OffsetKey>("JavaMethodCallInsertHandler.refStart")
